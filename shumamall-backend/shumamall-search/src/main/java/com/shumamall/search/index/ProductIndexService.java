package com.shumamall.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品索引管理服务。
 * <p>
 * 维护独立索引 {@code shumamall_product_search}（与 agent 内嵌的
 * {@code shumamall_product} 索引区分，避免 mapping 冲突互相覆盖）。
 * mapping 定义：
 * <ul>
 *   <li>name/subtitle/description：text + 自定义分析器 {@code shumamall_text}
 *       （html_strip 字符过滤器 + ik_max_word 分词器），供 BM25 中文关键词检索；
 *       与 {@code shumamall-agent} 的 {@code shumamall_product} 索引保持同一套分析链，
 *       避免同一商品文本在两套索引下切词不一致</li>
 *   <li>categoryName/brandName/mainImage：keyword，精确匹配/展示</li>
 *   <li>price/salesVolume/createdAt：数值/日期，支持排序与区间过滤</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductIndexService {

    /** 商品搜索索引名 */
    public static final String PRODUCT_INDEX = "shumamall_product_search";

    /** 自定义文本分析器名：html_strip + ik_max_word（与 agent 侧命名一致） */
    public static final String TEXT_ANALYZER = "shumamall_text";

    /** 中文分词器（依赖 ES 安装 analysis-ik 插件） */
    private static final String TOKENIZER_IK = "ik_max_word";

    private final ElasticsearchClient esClient;

    public ProductIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 幂等创建商品索引。
     *
     * @return true 表示本次创建成功；false 表示已存在或创建失败
     */
    public boolean createIndexIfAbsent() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(PRODUCT_INDEX)))
                    .value();
            if (exists) {
                log.debug("商品索引已存在，跳过创建: index={}", PRODUCT_INDEX);
                return false;
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
                            .properties("description", Property.of(p -> p.text(t -> t.analyzer(TEXT_ANALYZER))))
                            .properties("categoryId", Property.of(p -> p.long_(l -> l)))
                            .properties("categoryName", Property.of(p -> p.keyword(k -> k)))
                            .properties("brandId", Property.of(p -> p.long_(l -> l)))
                            .properties("brandName", Property.of(p -> p.keyword(k -> k)))
                            .properties("price", Property.of(p -> p.double_(d -> d)))
                            .properties("salesVolume", Property.of(p -> p.integer(i -> i)))
                            .properties("status", Property.of(p -> p.integer(i -> i)))
                            .properties("isNew", Property.of(p -> p.boolean_(b2 -> b2)))
                            .properties("isHot", Property.of(p -> p.boolean_(b2 -> b2)))
                            .properties("mainImage", Property.of(p -> p.keyword(k -> k)))
                            .properties("createdAt", Property.of(p -> p.date(d -> d
                                    .format("strict_date_optional_time||epoch_millis"))))));

            boolean acknowledged = esClient.indices().create(request).acknowledged();
            log.info("商品索引创建完成: index={}, analyzer={}, acknowledged={}",
                    PRODUCT_INDEX, TEXT_ANALYZER, acknowledged);
            return acknowledged;
        } catch (Exception e) {
            log.error("商品索引创建失败: index={}, err={}", PRODUCT_INDEX, e.getMessage());
            return false;
        }
    }

    /**
     * 删除商品索引（重建索引前调用）。
     */
    public void deleteIndex() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(PRODUCT_INDEX)))
                    .value();
            if (!exists) {
                log.debug("商品索引不存在，无需删除: index={}", PRODUCT_INDEX);
                return;
            }
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(PRODUCT_INDEX)));
            log.info("商品索引已删除: index={}", PRODUCT_INDEX);
        } catch (Exception e) {
            log.error("商品索引删除失败: index={}, err={}", PRODUCT_INDEX, e.getMessage());
        }
    }
}
