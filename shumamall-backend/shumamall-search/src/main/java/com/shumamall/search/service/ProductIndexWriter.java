package com.shumamall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.search.document.ProductDoc;
import com.shumamall.search.dto.BrandDTO;
import com.shumamall.search.dto.CategoryDTO;
import com.shumamall.search.dto.ProductDTO;
import com.shumamall.search.feign.BrandFeignClient;
import com.shumamall.search.feign.CategoryFeignClient;
import com.shumamall.search.feign.ProductFeignClient;
import com.shumamall.search.index.ProductIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品索引灌库服务（全量重建 + 单文档同步）。
 * <p>
 * 全量重建：删除索引 → 重建 mapping → 拉取分类/品牌映射 → 分页拉商品 → bulk 写入。
 * 增量同步（RabbitMQ 消费）复用 {@link #syncDoc(ProductDTO)} / {@link #deleteDoc(Long)}。
 */
@Slf4j
@Service
public class ProductIndexWriter {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ProductFeignClient productFeignClient;
    private final CategoryFeignClient categoryFeignClient;
    private final BrandFeignClient brandFeignClient;
    private final ElasticsearchClient esClient;
    private final ProductIndexService indexService;
    private final int batchSize;

    public ProductIndexWriter(ProductFeignClient productFeignClient,
                              CategoryFeignClient categoryFeignClient,
                              BrandFeignClient brandFeignClient,
                              ElasticsearchClient esClient,
                              ProductIndexService indexService,
                              @Value("${shumamall.search.index-batch-size:100}") int batchSize) {
        this.productFeignClient = productFeignClient;
        this.categoryFeignClient = categoryFeignClient;
        this.brandFeignClient = brandFeignClient;
        this.esClient = esClient;
        this.indexService = indexService;
        this.batchSize = batchSize <= 0 ? DEFAULT_PAGE_SIZE : batchSize;
    }

    /**
     * 全量重建索引。
     *
     * @return 实际写入的商品数量
     */
    public int rebuildIndex() {
        // 先删除重建，保证 mapping 与当前代码一致
        indexService.deleteIndex();
        if (!indexService.createIndexIfAbsent()) {
            log.error("重建索引失败：索引创建未成功，中止灌库");
            return 0;
        }
        List<ProductDoc> docs = loadAllProducts();
        if (docs.isEmpty()) {
            log.warn("灌库未拉取到任何商品，跳过写入");
            return 0;
        }
        int written = bulkWrite(docs);
        log.info("灌库完成: total={}, written={}", docs.size(), written);
        return written;
    }

    /**
     * 同步单个商品（增量同步：新增/更新）。
     * <p>
     * 商品已下架或不存在时从索引移除，保证前台只搜得到上架商品。
     */
    public void syncDoc(ProductDTO dto) throws IOException {
        if (dto == null || dto.getId() == null) {
            return;
        }
        if (dto.getStatus() == null || dto.getStatus() != 1) {
            deleteDoc(dto.getId());
            return;
        }
        ProductDoc doc = toDoc(dto, loadCategoryNames(), loadBrandNames());
        // 不吞掉 ES 异常：写索引失败必须让消费端感知，否则消息会被 ack 而索引永久缺失，
        // 造成「数据库有、索引没有」的静默不一致。异常向上抛，由消费端重试或转死信队列。
        esClient.index(i -> i
                .index(ProductIndexService.PRODUCT_INDEX)
                .id(String.valueOf(doc.getId()))
                .document(doc));
        log.debug("商品索引同步完成: id={}", doc.getId());
    }

    /**
     * 删除单个商品索引文档。
     * <p>
     * 文档本就不存在时 ES 返回 404，此时删除视为成功（删除天然幂等）；
     * 其余失败（集群不可用等）必须抛出，交由消费端重试/转死信。
     */
    public void deleteDoc(Long productId) throws IOException {
        try {
            esClient.delete(d -> d
                    .index(ProductIndexService.PRODUCT_INDEX)
                    .id(String.valueOf(productId)));
            log.debug("商品索引文档已删除: id={}", productId);
        } catch (ElasticsearchException e) {
            if (e.response() != null && e.response().status() == 404) {
                log.debug("商品索引文档不存在，无需删除: id={}", productId);
                return;
            }
            throw e;
        }
    }

    /**
     * 分页拉取全部商品（含下架，供管理端搜索）并转换为索引文档。
     */
    private List<ProductDoc> loadAllProducts() {
        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> brandNames = loadBrandNames();

        List<ProductDoc> docs = new ArrayList<>();
        int page = 1;
        while (true) {
            R<PageResult<ProductDTO>> resp = productFeignClient.list(
                    page, DEFAULT_PAGE_SIZE, null, null, null, null, null, null, null, null, null);
            if (!resp.isSuccess() || resp.getData() == null) {
                log.warn("分页拉取失败，终止灌库: page={}", page);
                break;
            }
            PageResult<ProductDTO> pageResult = resp.getData();
            List<ProductDTO> records = pageResult.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            records.forEach(item -> docs.add(toDoc(item, categoryNames, brandNames)));
            if (page >= pageResult.getPages() || records.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
        }
        return docs;
    }

    /**
     * 拉取分类树并展平为 id→name 映射。
     */
    private Map<Long, String> loadCategoryNames() {
        Map<Long, String> map = new HashMap<>();
        try {
            R<List<CategoryDTO>> resp = categoryFeignClient.tree();
            if (resp.isSuccess() && resp.getData() != null) {
                resp.getData().forEach(node -> flattenCategory(node, map));
            }
        } catch (Exception e) {
            log.warn("拉取分类树失败，分类名称将为空: err={}", e.getMessage());
        }
        return map;
    }

    private void flattenCategory(CategoryDTO node, Map<Long, String> map) {
        if (node.getId() != null && node.getName() != null) {
            map.put(node.getId(), node.getName());
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> flattenCategory(child, map));
        }
    }

    /**
     * 拉取全部分品牌并建立 id→name 映射。
     */
    private Map<Long, String> loadBrandNames() {
        Map<Long, String> map = new HashMap<>();
        try {
            int page = 1;
            while (true) {
                R<PageResult<BrandDTO>> resp = brandFeignClient.list(page, 100);
                if (!resp.isSuccess() || resp.getData() == null) {
                    break;
                }
                PageResult<BrandDTO> pageResult = resp.getData();
                List<BrandDTO> records = pageResult.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                records.forEach(b -> {
                    if (b.getId() != null && b.getName() != null) {
                        map.put(b.getId(), b.getName());
                    }
                });
                if (page >= pageResult.getPages() || records.size() < 100) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.warn("拉取品牌列表失败，品牌名称将为空: err={}", e.getMessage());
        }
        return map;
    }

    /**
     * 商品 DTO → ES 索引文档。
     */
    private ProductDoc toDoc(ProductDTO item, Map<Long, String> categoryNames, Map<Long, String> brandNames) {
        ProductDoc doc = new ProductDoc();
        doc.setId(item.getId());
        doc.setName(item.getName());
        doc.setSubtitle(item.getSubtitle());
        doc.setDescription(item.getDescription());
        doc.setCategoryId(item.getCategoryId());
        doc.setCategoryName(categoryNames.get(item.getCategoryId()));
        doc.setBrandId(item.getBrandId());
        doc.setBrandName(brandNames.get(item.getBrandId()));
        doc.setPrice(item.getPrice() == null ? null : item.getPrice().doubleValue());
        doc.setSalesVolume(item.getSalesVolume());
        doc.setStatus(item.getStatus());
        doc.setIsNew(item.getIsNew() != null && item.getIsNew() == 1);
        doc.setIsHot(item.getIsHot() != null && item.getIsHot() == 1);
        doc.setMainImage(item.getMainImage());
        doc.setCreatedAt(item.getCreatedAt() == null ? null : item.getCreatedAt().format(DATE_FORMATTER));
        return doc;
    }

    /**
     * 分批 bulk 写入 ES，返回成功写入条数。
     */
    private int bulkWrite(List<ProductDoc> docs) {
        int written = 0;
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<ProductDoc> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
            written += writeBatch(batch);
        }
        return written;
    }

    private int writeBatch(List<ProductDoc> batch) {
        List<BulkOperation> operations = batch.stream()
                .map(doc -> BulkOperation.of(o -> o.index(idx -> idx
                        .index(ProductIndexService.PRODUCT_INDEX)
                        .id(String.valueOf(doc.getId()))
                        .document(doc))))
                .toList();
        BulkRequest request = BulkRequest.of(b -> b.operations(operations));
        try {
            BulkResponse resp = esClient.bulk(request);
            if (resp.errors()) {
                log.warn("批量写入存在失败项: batchSize={}", batch.size());
            }
            return resp.items().size();
        } catch (Exception e) {
            log.error("批量写入 ES 失败: batchSize={}, err={}", batch.size(), e.getMessage());
            return 0;
        }
    }
}
