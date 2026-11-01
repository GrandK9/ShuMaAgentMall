package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.agent.search.IndexExternalVersion;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 商品向量化灌库服务（离线初始化链路）。
 * <p>
 * 从商品服务分页拉取全量在售商品，为每个商品生成语义向量并批量写入 ES，
 * 使 Agent 的 Hybrid Search（BM25 + KNN）具备数据基础。
 * 流程：创建索引（幂等） → 每拉一页就向量化并 bulk 写入。
 */
@Slf4j
@Service
public class ProductIndexWriter {

    /** 重建进行中时，值为正在灌库的物理索引名。增量据此双写。 */
    static final String REBUILD_TARGET_KEY = "agent:index:rebuild:target";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final String VERSION_CONFLICT = "version_conflict_engine_exception";

    private final ProductFeignClient productFeignClient;
    private final ProductEmbeddingService embeddingService;
    private final ElasticsearchClient esClient;
    private final ProductIndexService indexService;
    private final RedissonClient redissonClient;
    private final int batchSize;

    public ProductIndexWriter(ProductFeignClient productFeignClient,
                              ProductEmbeddingService embeddingService,
                              ElasticsearchClient esClient,
                              ProductIndexService indexService,
                              RedissonClient redissonClient,
                              @Value("${shumamall.agent.search.index.batch-size:100}") int batchSize) {
        this.productFeignClient = productFeignClient;
        this.embeddingService = embeddingService;
        this.esClient = esClient;
        this.indexService = indexService;
        this.redissonClient = redissonClient;
        this.batchSize = batchSize <= 0 ? DEFAULT_PAGE_SIZE : batchSize;
    }

    /**
     * 全量重建：新物理索引灌库，成功后切换别名。失败时删除半成品，别名保持原指向。
     *
     * @return 实际接受的写入条数
     */
    public int rebuildIndex() {
        String physical = indexService.newPhysicalName();
        if (!indexService.createPhysicalIndex(physical, ProductIndexService.REFRESH_BULK)) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "物理索引创建失败");
        }
        rebuildTarget().set(physical);
        try {
            int written = writeAll(physical, true);
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
     * 全量灌库：创建索引后分页拉取所有在售商品并写入 ES。
     *
     * @return 实际写入的商品数量（0 表示无数据或全部失败）
     */
    public int indexAllProducts() {
        return writeAll(ProductIndexService.PRODUCT_INDEX, false);
    }

    /**
     * @param strict true 时拉取失败或写入失败直接中止，供别名切换前使用
     */
    private int writeAll(String target, boolean strict) {
        int written = 0;
        int total = 0;
        int page = 1;
        while (true) {
            long fetchedAt = System.currentTimeMillis();
            R<PageResult<ProductItemDTO>> resp = productFeignClient.list(
                    page, DEFAULT_PAGE_SIZE, null, null, null, 1, null, null, null, null, null);
            if (!resp.isSuccess() || resp.getData() == null) {
                log.warn("分页拉取失败，终止灌库: page={}, written={}", page, written);
                if (strict) {
                    throw new BusinessException(ResultCode.SERVER_ERROR, "分页拉取商品失败，已中止重建");
                }
                break;
            }
            PageResult<ProductItemDTO> pageResult = resp.getData();
            List<ProductItemDTO> records = pageResult.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            List<ProductDoc> pageDocs = records.stream().map(this::toDoc).toList();
            long version = IndexExternalVersion.snapshot(fetchedAt);
            written += strict ? bulkWriteStrict(target, pageDocs, version) : bulkWrite(target, pageDocs, version);
            total += pageDocs.size();
            if (page >= pageResult.getPages() || records.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
        }
        if (total == 0) {
            log.warn("灌库未拉取到任何商品，跳过写入");
            return 0;
        }
        log.info("灌库完成: index={}, total={}, written={}", target, total, written);
        return written;
    }

    /**
     * 增量同步单个商品（商品新增/更新事件）。
     * <p>
     * 重新拉取商品详情并向量化后覆写索引文档；商品不存在或已下架则从索引移除，
     * 保证检索只命中在售商品（索引与 MySQL 最终一致）。
     *
     * @param productId 商品 ID
     */
    public void syncProduct(Long productId) throws IOException {
        if (productId == null) {
            return;
        }
        // 不吞掉 Feign 与 ES 异常：拉不到商品详情或写索引失败都必须让消费端感知，
        // 否则消息被 ack 而向量索引未更新，检索结果与数据库静默不一致。
        // 异常向上抛，由消费端重试或转死信队列。
        R<ProductItemDTO> resp = productFeignClient.detail(productId);
        if (!resp.isSuccess() || resp.getData() == null) {
            // 商品不存在或已下架 → 从索引移除
            deleteDoc(productId);
            return;
        }

        ProductDoc doc = toDoc(resp.getData());
        long version = IndexExternalVersion.live(System.currentTimeMillis());
        indexDoc(ProductIndexService.PRODUCT_INDEX, doc, version);
        String target = currentRebuildTarget();
        if (target != null) {
            indexDoc(target, doc, version);
        }
        log.debug("商品索引增量同步完成: id={}", doc.getId());
    }

    /**
     * 删除单个商品索引文档。
     * <p>
     * 文档本就不存在时 ES 返回 404，此时删除视为成功（删除天然幂等）；
     * 其余失败（集群不可用等）必须抛出，交由消费端重试/转死信。
     */
    public void deleteDoc(Long productId) throws IOException {
        if (productId == null) {
            return;
        }
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
            if (e.response() != null && e.response().status() == 409) {
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
            if (e.response() != null && (e.response().status() == 404 || e.response().status() == 409)) {
                log.debug("商品索引文档无需删除: index={}, id={}", index, productId);
                return;
            }
            throw e;
        }
    }

    private String currentRebuildTarget() {
        if (redissonClient == null) {
            return null;
        }
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
     * 商品 DTO → ES 索引文档（含语义向量）。
     */
    private ProductDoc toDoc(ProductItemDTO item) {
        String text = joinText(item);
        ProductDoc doc = new ProductDoc();
        doc.setId(item.getId());
        doc.setName(item.getName());
        doc.setSubtitle(item.getSubtitle());
        doc.setBrand(item.getBrandName());
        doc.setCategory(item.getCategoryName());
        doc.setDescription(item.getDescription());
        doc.setPrice(item.getPrice());
        doc.setStock(item.getStock());
        doc.setSalesVolume(item.getSalesVolume());
        doc.setEmbedding(embeddingService.embed(text));
        return doc;
    }

    /**
     * 向量化文本：名称 + 副标题 + 品牌 + 分类。
     * <p>
     * 只取语义密度高的短文本，不包含 description（HTML 详情会引入大量噪点、稀释语义信号）。
     */
    private String joinText(ProductItemDTO item) {
        StringBuilder sb = new StringBuilder();
        if (item.getName() != null) {
            sb.append(item.getName());
        }
        if (item.getSubtitle() != null && !item.getSubtitle().isBlank()) {
            sb.append(' ').append(item.getSubtitle());
        }
        if (item.getBrandName() != null && !item.getBrandName().isBlank()) {
            sb.append(' ').append(item.getBrandName());
        }
        if (item.getCategoryName() != null && !item.getCategoryName().isBlank()) {
            sb.append(' ').append(item.getCategoryName());
        }
        return sb.toString();
    }

    /**
     * 分批 bulk 写入 ES，返回成功写入条数。
     */
    private int bulkWrite(String target, List<ProductDoc> docs, long version) {
        int written = 0;
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<ProductDoc> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
            written += writeBatch(target, batch, version, false);
        }
        return written;
    }

    private int bulkWriteStrict(String target, List<ProductDoc> docs, long version) {
        int written = 0;
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<ProductDoc> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
            written += writeBatch(target, batch, version, true);
        }
        return written;
    }

    private int writeBatch(String target, List<ProductDoc> batch, long version, boolean strict) {
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
                if (strict) {
                    throw new BusinessException(ResultCode.SERVER_ERROR, "批量写入存在失败项");
                }
            }
            if (!strict && resp.errors()) {
                log.warn("批量写入存在失败项: batchSize={}", batch.size());
            }
            return strict ? accepted : resp.items().size();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量写入 ES 失败: index={}, batchSize={}, err={}", target, batch.size(), e.getMessage());
            if (strict) {
                throw new BusinessException(ResultCode.SERVER_ERROR, "批量写入 ES 失败");
            }
            return 0;
        }
    }
}
