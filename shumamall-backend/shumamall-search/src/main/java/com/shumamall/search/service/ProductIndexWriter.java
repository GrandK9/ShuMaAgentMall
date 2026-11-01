package com.shumamall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.search.document.ProductDoc;
import com.shumamall.search.dto.BrandDTO;
import com.shumamall.search.dto.CategoryDTO;
import com.shumamall.search.dto.ProductDTO;
import com.shumamall.search.feign.BrandFeignClient;
import com.shumamall.search.feign.CategoryFeignClient;
import com.shumamall.search.feign.ProductFeignClient;
import com.shumamall.search.index.IndexExternalVersion;
import com.shumamall.search.index.ProductIndexService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品索引灌库（别名切换的全量重建 + 单文档增量）。
 * <p>
 * 全量重建写到新的物理索引，灌完再把别名切过去。重建期间增量同时写别名和新物理索引，
 * 并用外部版本号避免旧页覆盖更新的增量。
 */
@Slf4j
@Service
public class ProductIndexWriter {

    /** 重建进行中时，值为正在灌库的物理索引名。增量据此双写。 */
    static final String REBUILD_TARGET_KEY = "search:index:rebuild:target";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final String VERSION_CONFLICT = "version_conflict_engine_exception";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ProductFeignClient productFeignClient;
    private final CategoryFeignClient categoryFeignClient;
    private final BrandFeignClient brandFeignClient;
    private final ElasticsearchClient esClient;
    private final ProductIndexService indexService;
    private final RedissonClient redissonClient;
    private final int batchSize;

    public ProductIndexWriter(ProductFeignClient productFeignClient,
                              CategoryFeignClient categoryFeignClient,
                              BrandFeignClient brandFeignClient,
                              ElasticsearchClient esClient,
                              ProductIndexService indexService,
                              RedissonClient redissonClient,
                              @Value("${shumamall.search.index-batch-size:100}") int batchSize) {
        this.productFeignClient = productFeignClient;
        this.categoryFeignClient = categoryFeignClient;
        this.brandFeignClient = brandFeignClient;
        this.esClient = esClient;
        this.indexService = indexService;
        this.redissonClient = redissonClient;
        this.batchSize = batchSize <= 0 ? DEFAULT_PAGE_SIZE : batchSize;
    }

    /**
     * 全量重建：新物理索引灌库，成功后切换别名。失败时删除半成品，别名保持原指向。
     *
     * @return 实际接受的写入条数（含因版本更旧被拒绝的条数）
     */
    public int rebuildIndex() {
        String physical = indexService.newPhysicalName();
        if (!indexService.createPhysicalIndex(physical, ProductIndexService.REFRESH_BULK)) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "物理索引创建失败");
        }
        rebuildTarget().set(physical);
        try {
            int written = writeAllProducts(physical);
            indexService.putRefreshInterval(physical, ProductIndexService.REFRESH_NORMAL);
            indexService.refresh(physical);
            indexService.promote(physical);
            log.info("灌库完成并已切换别名: index={}, written={}", physical, written);
            return written;
        } catch (Exception e) {
            indexService.deletePhysical(physical);
            log.error("全量重建失败，别名未切换: index={}, err={}", physical, e.getMessage());
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ResultCode.SERVER_ERROR, "全量重建失败");
        } finally {
            rebuildTarget().delete();
        }
    }

    /**
     * 分页拉取商品，每拿到一页就按该页的拉取时刻写入目标索引。
     */
    private int writeAllProducts(String target) {
        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> brandNames = loadBrandNames();
        int written = 0;
        int total = 0;
        int page = 1;
        while (true) {
            long fetchedAt = System.currentTimeMillis();
            R<PageResult<ProductDTO>> resp = productFeignClient.list(
                    page, DEFAULT_PAGE_SIZE, null, null, null, null, null, null, null, null, null);
            if (!resp.isSuccess() || resp.getData() == null) {
                throw new BusinessException(ResultCode.SERVER_ERROR, "分页拉取商品失败，已中止重建");
            }
            PageResult<ProductDTO> pageResult = resp.getData();
            List<ProductDTO> records = pageResult.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            List<ProductDoc> pageDocs = records.stream()
                    .map(item -> toDoc(item, categoryNames, brandNames))
                    .toList();
            written += bulkWrite(target, pageDocs, IndexExternalVersion.snapshot(fetchedAt));
            total += pageDocs.size();
            if (page >= pageResult.getPages() || records.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
        }
        if (total == 0) {
            log.warn("灌库未拉取到任何商品");
        }
        return written;
    }

    /**
     * 同步单个商品（增量）。重建进行中时同时写入别名和正在灌库的物理索引。
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
        long version = IndexExternalVersion.live(System.currentTimeMillis());
        indexDoc(ProductIndexService.PRODUCT_INDEX, doc, version);
        String target = currentRebuildTarget();
        if (target != null) {
            indexDoc(target, doc, version);
        }
        log.debug("商品索引同步完成: id={}", doc.getId());
    }

    /**
     * 删除单个商品索引文档。文档不存在或版本更旧都视为删除已达成。
     */
    public void deleteDoc(Long productId) throws IOException {
        long version = IndexExternalVersion.live(System.currentTimeMillis());
        deleteFrom(ProductIndexService.PRODUCT_INDEX, productId, version);
        String target = currentRebuildTarget();
        if (target != null) {
            deleteFrom(target, productId, version);
        }
    }

    private void indexDoc(String index, ProductDoc doc, long version) throws IOException {
        try {
            esClient.index(i -> i
                    .index(index)
                    .id(String.valueOf(doc.getId()))
                    .document(doc)
                    .version(version)
                    .versionType(VersionType.External));
        } catch (ElasticsearchException e) {
            if (isConflict(e)) {
                log.debug("版本较旧，跳过写入: index={}, id={}", index, doc.getId());
                return;
            }
            throw e;
        }
    }

    private void deleteFrom(String index, Long productId, long version) throws IOException {
        try {
            esClient.delete(d -> d
                    .index(index)
                    .id(String.valueOf(productId))
                    .version(version)
                    .versionType(VersionType.External));
            log.debug("商品索引文档已删除: index={}, id={}", index, productId);
        } catch (ElasticsearchException e) {
            if (e.response() != null && (e.response().status() == 404 || isConflict(e))) {
                log.debug("商品索引文档无需删除: index={}, id={}, status={}",
                        index, productId, e.response().status());
                return;
            }
            throw e;
        }
    }

    private String currentRebuildTarget() {
        String target = rebuildTarget().get();
        if (target == null || target.isBlank() || target.equals(ProductIndexService.PRODUCT_INDEX)) {
            return null;
        }
        return target;
    }

    private RBucket<String> rebuildTarget() {
        return redissonClient.getBucket(REBUILD_TARGET_KEY);
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
     * 分批 bulk 写入目标索引，返回被接受的条数。版本冲突视为旧数据被拒绝，不算失败。
     */
    private int bulkWrite(String target, List<ProductDoc> docs, long version) {
        int written = 0;
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<ProductDoc> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
            written += writeBatch(target, batch, version);
        }
        return written;
    }

    private int writeBatch(String target, List<ProductDoc> batch, long version) {
        List<BulkOperation> operations = batch.stream()
                .map(doc -> BulkOperation.of(o -> o.index(idx -> idx
                        .index(target)
                        .id(String.valueOf(doc.getId()))
                        .document(doc)
                        .version(version)
                        .versionType(VersionType.External))))
                .toList();
        BulkRequest request = BulkRequest.of(b -> b.operations(operations));
        try {
            BulkResponse resp = esClient.bulk(request);
            int accepted = 0;
            for (BulkResponseItem item : resp.items()) {
                if (item.error() == null || VERSION_CONFLICT.equals(item.error().type())) {
                    accepted++;
                    continue;
                }
                log.warn("批量写入失败: index={}, id={}, type={}", target, item.id(), item.error().type());
                throw new BusinessException(ResultCode.SERVER_ERROR, "批量写入存在失败项");
            }
            return accepted;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量写入 ES 失败: index={}, batchSize={}, err={}", target, batch.size(), e.getMessage());
            throw new BusinessException(ResultCode.SERVER_ERROR, "批量写入 ES 失败");
        }
    }

    private static boolean isConflict(ElasticsearchException e) {
        return e.response() != null && e.response().status() == 409;
    }
}
