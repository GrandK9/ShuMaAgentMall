package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品向量化灌库服务（离线初始化链路）。
 * <p>
 * 从商品服务分页拉取全量在售商品，为每个商品生成语义向量并批量写入 ES，
 * 使 Agent 的 Hybrid Search（BM25 + KNN）具备数据基础。
 * 流程：创建索引（幂等） → 分页拉取商品 → 向量化 → bulk 批量写入。
 */
@Slf4j
@Service
public class ProductIndexWriter {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final ProductFeignClient productFeignClient;
    private final ProductEmbeddingService embeddingService;
    private final ElasticsearchClient esClient;
    private final int batchSize;

    public ProductIndexWriter(ProductFeignClient productFeignClient,
                              ProductEmbeddingService embeddingService,
                              ElasticsearchClient esClient,
                              @Value("${shumamall.agent.search.index.batch-size:100}") int batchSize) {
        this.productFeignClient = productFeignClient;
        this.embeddingService = embeddingService;
        this.esClient = esClient;
        this.batchSize = batchSize <= 0 ? DEFAULT_PAGE_SIZE : batchSize;
    }

    /**
     * 全量灌库：创建索引后分页拉取所有在售商品并写入 ES。
     *
     * @return 实际写入的商品数量（0 表示无数据或全部失败）
     */
    public int indexAllProducts() {
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
        esClient.index(i -> i
                .index(ProductIndexService.PRODUCT_INDEX)
                .id(String.valueOf(doc.getId()))
                .document(doc));
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
     * 分页拉取全部在售商品并向量化。
     */
    private List<ProductDoc> loadAllProducts() {
        List<ProductDoc> docs = new ArrayList<>();
        int page = 1;
        while (true) {
            R<PageResult<ProductItemDTO>> resp = productFeignClient.list(
                    page, DEFAULT_PAGE_SIZE, null, null, null, 1, null, null, null, null, null);
            if (!resp.isSuccess() || resp.getData() == null) {
                log.warn("分页拉取失败，终止灌库: page={}", page);
                break;
            }
            PageResult<ProductItemDTO> pageResult = resp.getData();
            List<ProductItemDTO> records = pageResult.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            records.forEach(item -> docs.add(toDoc(item)));
            if (page >= pageResult.getPages() || records.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
        }
        return docs;
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
