package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品索引管理服务。
 * <p>
 * 幂等创建 {@code shumamall_product} 索引，mapping 定义：
 * <ul>
 *   <li>文本字段（name/subtitle/description）：使用自定义分析器 {@code shumamall_text}
 *       （html_strip 字符过滤器 + ik_max_word 分词器），供 BM25 中文关键词检索；
 *       html_strip 用于剔除商品详情 HTML 标签带来的噪声词</li>
 *   <li>品牌/分类：text（可分词参与检索）+ keyword 子字段（保留精确过滤能力）</li>
 *   <li>embedding：dense_vector，1024 维、cosine 相似度、HNSW 近似最近邻索引，供 KNN 语义检索</li>
 * </ul>
 * 新建索引字段校验失败不会回滚（ES 不支持事务），因此采用"存在即跳过"的幂等策略。
 */
@Slf4j
@Service
public class ProductIndexService {

    /** 商品索引名（与 ProductHybridSearchService 保持一致） */
    public static final String PRODUCT_INDEX = "shumamall_product";

    /** embedding 维度（与选用 embedding 模型的输出维度一致，如 bge-large-zh = 1024） */
    public static final int EMBEDDING_DIMS = 1024;

    /** 自定义文本分析器名：html_strip + ik_max_word */
    public static final String TEXT_ANALYZER = "shumamall_text";

    /** 中文分词器（依赖 ES 安装 analysis-ik 插件） */
    private static final String TOKENIZER_IK = "ik_max_word";

    private final ElasticsearchClient esClient;

    public ProductIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 幂等创建商品索引（已存在则跳过）。
     *
     * @return true 表示本次创建成功；false 表示已存在或创建失败
     */
    public boolean createIndexIfAbsent() {
        return createIndex(false);
    }

    /**
     * 创建商品索引。
     *
     * @param recreate true = 先删除已有索引再重建（mapping/分词器变更时使用）
     * @return true 表示创建成功
     */
    public boolean createIndex(boolean recreate) {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(PRODUCT_INDEX)))
                    .value();
            if (exists) {
                if (!recreate) {
                    log.debug("商品索引已存在，跳过创建: index={}", PRODUCT_INDEX);
                    return false;
                }
                esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(PRODUCT_INDEX)));
                log.info("商品索引已删除，准备重建: index={}", PRODUCT_INDEX);
            }

            CreateIndexRequest request = CreateIndexRequest.of(b -> b
                    .index(PRODUCT_INDEX)
                    .settings(s -> s
                            .analysis(a -> a
                                    .analyzer(TEXT_ANALYZER, an -> an
                                            .custom(c -> c
                                                    .tokenizer(TOKENIZER_IK)
                                                    .charFilter("html_strip")))))
                    .mappings(m -> m
                            .properties("id", Property.of(p -> p.long_(l -> l)))
                            .properties("name", Property.of(p -> p.text(t -> t.analyzer(TEXT_ANALYZER))))
                            .properties("subtitle", Property.of(p -> p.text(t -> t.analyzer(TEXT_ANALYZER))))
                            .properties("brand", Property.of(p -> p.text(t -> t
                                    .analyzer(TEXT_ANALYZER)
                                    .fields("keyword", Property.of(k -> k.keyword(kw -> kw.ignoreAbove(256)))))))
                            .properties("category", Property.of(p -> p.text(t -> t
                                    .analyzer(TEXT_ANALYZER)
                                    .fields("keyword", Property.of(k -> k.keyword(kw -> kw.ignoreAbove(256)))))))
                            .properties("price", Property.of(p -> p.double_(d -> d)))
                            .properties("stock", Property.of(p -> p.integer(i -> i)))
                            .properties("salesVolume", Property.of(p -> p.integer(i -> i)))
                            .properties("description", Property.of(p -> p.text(t -> t.analyzer(TEXT_ANALYZER))))
                            .properties("embedding", Property.of(p -> p.denseVector(d -> d
                                    .dims(EMBEDDING_DIMS)
                                    .index(true)
                                    .similarity(DenseVectorSimilarity.Cosine)
                                    .indexOptions(o -> o
                                            .type(DenseVectorIndexOptionsType.Hnsw)
                                            .m(16)
                                            .efConstruction(100)))))));

            boolean acknowledged = esClient.indices().create(request).acknowledged();
            log.info("商品索引创建完成: index={}, analyzer={}, acknowledged={}",
                    PRODUCT_INDEX, TEXT_ANALYZER, acknowledged);
            return acknowledged;
        } catch (Exception e) {
            log.error("商品索引创建失败: index={}, err={}", PRODUCT_INDEX, e.getMessage());
            return false;
        }
    }
}
