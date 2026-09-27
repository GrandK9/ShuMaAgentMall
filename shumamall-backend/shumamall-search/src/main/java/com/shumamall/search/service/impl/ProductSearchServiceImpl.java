package com.shumamall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.search.document.ProductDoc;
import com.shumamall.search.dto.ProductDTO;
import com.shumamall.search.dto.ProductSearchVO;
import com.shumamall.search.feign.ProductFeignClient;
import com.shumamall.search.index.ProductIndexService;
import com.shumamall.search.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 商品搜索服务实现。
 * <p>
 * 基于 ES 的 bool 查询：关键词走 multi_match（名称权重最高），
 * 分类/品牌/价格/上架状态走 filter（不影响相关度）。
 * ES 不可用时降级调用商品服务列表接口，保证前台搜索可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final String SORT_SALES = "sales";
    private static final String SORT_PRICE_ASC = "price_asc";
    private static final String SORT_PRICE_DESC = "price_desc";
    private static final String SORT_NEWEST = "newest";

    private final ElasticsearchClient esClient;
    private final ProductFeignClient productFeignClient;

    @Override
    public PageResult<ProductSearchVO> search(String keyword, Long categoryId, List<Long> brandIds,
                                              Double priceMin, Double priceMax, String sortBy,
                                              int page, int size) {
        try {
            return searchFromEs(keyword, categoryId, brandIds, priceMin, priceMax, sortBy, page, size);
        } catch (Exception e) {
            log.error("ES 搜索失败，降级到商品服务列表: err={}", e.getMessage());
            return searchFromProduct(keyword, categoryId, brandIds, sortBy, page, size);
        }
    }

    /**
     * ES 全文检索。
     */
    private PageResult<ProductSearchVO> searchFromEs(String keyword, Long categoryId, List<Long> brandIds,
                                                     Double priceMin, Double priceMax, String sortBy,
                                                     int page, int size) throws IOException {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 关键词：multi_match，名称权重最高
        if (StringUtils.hasText(keyword)) {
            MultiMatchQuery multiMatch = MultiMatchQuery.of(m -> m
                    .query(keyword)
                    .fields("name^3", "subtitle^2", "description"));
            bool.must(multiMatch._toQuery());
        }

        // filter：分类/品牌/价格/上架状态
        if (categoryId != null) {
            bool.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
        }
        if (!CollectionUtils.isEmpty(brandIds)) {
            bool.filter(f -> f.terms(t -> t.field("brandId")
                    .terms(ts -> ts.value(brandIds.stream().map(l -> co.elastic.clients.elasticsearch._types.FieldValue.of(l)).toList()))));
        }
        if (priceMin != null || priceMax != null) {
            // 8.x Java client 中 RangeQuery 为 tagged union，价格区间用 untyped 变体
            bool.filter(f -> f.range(r -> r.untyped(u -> {
                u.field("price");
                if (priceMin != null) {
                    u.gte(JsonData.of(priceMin));
                }
                if (priceMax != null) {
                    u.lte(JsonData.of(priceMax));
                }
                return u;
            })));
        }
        // 前台只搜已上架商品
        bool.filter(f -> f.term(t -> t.field("status").value(1)));

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(ProductIndexService.PRODUCT_INDEX)
                .query(bool.build()._toQuery())
                .from((page - 1) * size)
                .size(size);

        // 排序
        List<SortOptions> sorts = buildSorts(sortBy);
        if (!sorts.isEmpty()) {
            request.sort(sorts);
        }

        SearchResponse<ProductDoc> resp = esClient.search(request.build(), ProductDoc.class);
        long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
        List<Hit<ProductDoc>> hits = resp.hits().hits();
        if (hits == null || hits.isEmpty()) {
            return new PageResult<>(page, size, total, Collections.emptyList());
        }

        List<ProductSearchVO> records = hits.stream().map(hit -> {
            ProductDoc doc = hit.source();
            ProductSearchVO vo = new ProductSearchVO();
            if (doc != null) {
                vo.setProductId(doc.getId());
                vo.setName(doc.getName());
                vo.setSubtitle(doc.getSubtitle());
                vo.setMainImage(doc.getMainImage());
                vo.setPrice(doc.getPrice());
                vo.setSalesVolume(doc.getSalesVolume());
                vo.setCategoryName(doc.getCategoryName());
                vo.setBrandName(doc.getBrandName());
            }
            vo.setScore(hit.score() == null ? null : hit.score().floatValue());
            return vo;
        }).toList();

        return new PageResult<>(page, size, total, records);
    }

    /**
     * 降级：调用商品服务管理端列表接口（product 无鉴权）。
     */
    private PageResult<ProductSearchVO> searchFromProduct(String keyword, Long categoryId, List<Long> brandIds,
                                                          String sortBy, int page, int size) {
        Long brandId = CollectionUtils.isEmpty(brandIds) ? null : brandIds.get(0);
        String[] sortMapping = mapSortForProduct(sortBy);
        R<com.shumamall.common.result.PageResult<ProductDTO>> resp = productFeignClient.list(
                page, size, categoryId, brandId, keyword, 1, null, null, null,
                sortMapping[0], sortMapping[1]);
        if (!resp.isSuccess() || resp.getData() == null) {
            return new PageResult<>(page, size, 0, Collections.emptyList());
        }
        com.shumamall.common.result.PageResult<ProductDTO> data = resp.getData();
        List<ProductSearchVO> records = data.getRecords().stream().map(dto -> {
            ProductSearchVO vo = new ProductSearchVO();
            vo.setProductId(dto.getId());
            vo.setName(dto.getName());
            vo.setSubtitle(dto.getSubtitle());
            vo.setMainImage(dto.getMainImage());
            vo.setPrice(dto.getPrice() == null ? null : dto.getPrice().doubleValue());
            vo.setSalesVolume(dto.getSalesVolume());
            return vo;
        }).toList();
        return new PageResult<>(page, size, data.getTotal(), records);
    }

    /**
     * 排序参数 → ES sort 列表。
     */
    private List<SortOptions> buildSorts(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return Collections.emptyList();
        }
        List<SortOptions> sorts = new ArrayList<>();
        switch (sortBy) {
            case SORT_SALES -> sorts.add(SortOptions.of(s -> s.field(f -> f
                    .field("salesVolume").order(SortOrder.Desc))));
            case SORT_PRICE_ASC -> sorts.add(SortOptions.of(s -> s.field(f -> f
                    .field("price").order(SortOrder.Asc))));
            case SORT_PRICE_DESC -> sorts.add(SortOptions.of(s -> s.field(f -> f
                    .field("price").order(SortOrder.Desc))));
            case SORT_NEWEST -> sorts.add(SortOptions.of(s -> s.field(f -> f
                    .field("createdAt").order(SortOrder.Desc))));
            default -> {
                // default：按相关度 _score，不追加 sort
            }
        }
        return sorts;
    }

    /**
     * 排序参数 → product 列表接口的 sortBy/sortOrder。
     */
    private String[] mapSortForProduct(String sortBy) {
        return switch (sortBy) {
            case SORT_SALES -> new String[]{"sales_volume", "desc"};
            case SORT_PRICE_ASC -> new String[]{"price", "asc"};
            case SORT_PRICE_DESC -> new String[]{"price", "desc"};
            case SORT_NEWEST -> new String[]{"created_at", "desc"};
            default -> new String[]{null, "desc"};
        };
    }
}
