package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品索引管理服务。
 * <p>
 * 对外名字 {@code shumamall_product} 在全量重建后作为别名。mapping 定义：
 * <ul>
 *   <li>文本字段（name/subtitle/description/brand/category）：写入用自定义分析器
 *       {@code shumamall_text}（html_strip + ik_max_word），倒排里保留细词；
 *       查询用 IK 自带的 {@code ik_smart}。html_strip 只作用于写入，用来去掉详情里的标签</li>
 *   <li>品牌/分类：text 参与检索，另带 keyword 子字段做精确过滤</li>
 *   <li>embedding：dense_vector，1024 维、cosine 相似度、HNSW 近似最近邻索引，供 KNN 语义检索</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductIndexService {

    /** 商品索引别名（与 ProductHybridSearchService 保持一致） */
    public static final String PRODUCT_INDEX = "shumamall_product";

    /** embedding 维度（与选用 embedding 模型的输出维度一致，如 bge-large-zh = 1024） */
    public static final int EMBEDDING_DIMS = 1024;

    /** 写入分析器：html_strip + ik_max_word */
    public static final String TEXT_ANALYZER = "shumamall_text";

    /**
     * 查询分析器。IK 插件自带该分析器，mapping 可直接引用，不必在索引 settings 里再注册。
     * 粗切后的词能在细切倒排里找到，又不会把用户输入拆成碎词去匹配。
     */
    public static final String SEARCH_ANALYZER = "ik_smart";

    /** 线上搜索使用的 refresh 间隔。 */
    public static final String REFRESH_NORMAL = "1s";

    /** 灌库期间关闭自动 refresh。 */
    public static final String REFRESH_BULK = "-1";

    /** 写入用的中文分词器（依赖 ES 安装 analysis-ik 插件） */
    private static final String TOKENIZER_IK = "ik_max_word";

    private final ElasticsearchClient esClient;

    public ProductIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 幂等创建商品索引（已存在则跳过）。
     * <p>
     * 仅用于索引尚不存在时的初始化。全量重建走 {@link #createPhysicalIndex(String, String)} 与 {@link #promote(String)}。
     *
     * @return true 表示本次创建成功；false 表示已存在或创建失败
     */
    public boolean createIndexIfAbsent() {
        return createIndex(false);
    }

    /**
     * 创建与别名同名的物理索引。
     *
     * @param recreate true = 先删除已有同名索引再重建
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
            return createPhysicalIndex(PRODUCT_INDEX, null);
        } catch (Exception e) {
            log.error("商品索引创建失败: index={}, err={}", PRODUCT_INDEX, e.getMessage());
            return false;
        }
    }

    /**
     * 新建一轮重建使用的物理索引名。
     */
    public String newPhysicalName() {
        return PRODUCT_INDEX + "_v" + System.currentTimeMillis();
    }

    /**
     * 创建物理索引。
     *
     * @param refreshInterval 为 null 时使用 ES 默认 refresh
     * @return true 表示创建成功
     */
    public boolean createPhysicalIndex(String physicalIndex, String refreshInterval) {
        try {
            boolean acknowledged = esClient.indices()
                    .create(buildCreateRequest(physicalIndex, refreshInterval))
                    .acknowledged();
            log.info("商品物理索引创建完成: index={}, refresh={}, acknowledged={}",
                    physicalIndex, refreshInterval, acknowledged);
            return acknowledged;
        } catch (Exception e) {
            log.error("商品物理索引创建失败: index={}, err={}", physicalIndex, e.getMessage());
            return false;
        }
    }

    /**
     * 把别名切到新物理索引。首次从同名物理索引迁移时会先删掉该物理索引。
     * 上一代物理索引保留，更早的版本删除。
     */
    public void promote(String physicalIndex) throws IOException {
        List<String> currentTargets = aliasTargets();
        boolean concreteOccupiesName = currentTargets.isEmpty() && nameExists();
        if (concreteOccupiesName) {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(PRODUCT_INDEX)));
            log.info("已移除与别名同名的旧物理索引: index={}", PRODUCT_INDEX);
            esClient.indices().updateAliases(u -> u.actions(
                    Action.of(a -> a.add(ad -> ad.index(physicalIndex).alias(PRODUCT_INDEX)))));
        } else {
            List<Action> actions = new ArrayList<>();
            for (String old : currentTargets) {
                if (!old.equals(physicalIndex)) {
                    actions.add(Action.of(a -> a.remove(r -> r.index(old).alias(PRODUCT_INDEX))));
                }
            }
            actions.add(Action.of(a -> a.add(ad -> ad.index(physicalIndex).alias(PRODUCT_INDEX))));
            esClient.indices().updateAliases(u -> u.actions(actions));
        }
        log.info("别名已切换: alias={}, index={}", PRODUCT_INDEX, physicalIndex);
        pruneOldGenerations(physicalIndex, currentTargets);
    }

    public void putRefreshInterval(String physicalIndex, String refreshInterval) throws IOException {
        esClient.indices().putSettings(p -> p
                .index(physicalIndex)
                .settings(s -> s.refreshInterval(Time.of(t -> t.time(refreshInterval)))));
        log.info("已调整 refresh_interval: index={}, value={}", physicalIndex, refreshInterval);
    }

    public void refresh(String physicalIndex) throws IOException {
        esClient.indices().refresh(r -> r.index(physicalIndex));
    }

    public void deletePhysical(String physicalIndex) {
        if (physicalIndex == null || physicalIndex.equals(PRODUCT_INDEX)) {
            return;
        }
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(physicalIndex)))
                    .value();
            if (!exists) {
                return;
            }
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(physicalIndex)));
            log.info("已删除未切换的物理索引: index={}", physicalIndex);
        } catch (Exception e) {
            log.error("删除物理索引失败: index={}, err={}", physicalIndex, e.getMessage());
        }
    }

    public List<String> aliasTargets() {
        try {
            GetAliasResponse resp = esClient.indices().getAlias(g -> g.name(PRODUCT_INDEX));
            return new ArrayList<>(resp.result().keySet());
        } catch (ElasticsearchException e) {
            if (e.response() != null && e.response().status() == 404) {
                return List.of();
            }
            log.warn("查询别名失败: alias={}, err={}", PRODUCT_INDEX, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("查询别名失败: alias={}, err={}", PRODUCT_INDEX, e.getMessage());
            return List.of();
        }
    }

    public boolean nameExists() {
        try {
            return esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(PRODUCT_INDEX)))
                    .value();
        } catch (Exception e) {
            return false;
        }
    }

    private void pruneOldGenerations(String current, List<String> previous) {
        Set<String> keep = new LinkedHashSet<>();
        keep.add(current);
        keep.addAll(previous);
        keep.remove(PRODUCT_INDEX);
        try {
            GetIndexResponse response = esClient.indices().get(g -> g.index(PRODUCT_INDEX + "_v*"));
            for (String name : response.result().keySet()) {
                if (keep.contains(name)) {
                    continue;
                }
                esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(name)));
                log.info("已删除更早的物理索引: index={}", name);
            }
        } catch (ElasticsearchException e) {
            if (e.response() == null || e.response().status() != 404) {
                log.warn("清理旧物理索引失败: err={}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("清理旧物理索引失败: err={}", e.getMessage());
        }
    }

    private CreateIndexRequest buildCreateRequest(String index, String refreshInterval) {
        return CreateIndexRequest.of(b -> b
                .index(index)
                .settings(s -> {
                    s.analysis(a -> a
                            .analyzer(TEXT_ANALYZER, an -> an
                                    .custom(c -> c
                                            .tokenizer(TOKENIZER_IK)
                                            .charFilter("html_strip"))));
                    if (refreshInterval != null) {
                        s.refreshInterval(Time.of(t -> t.time(refreshInterval)));
                    }
                    return s;
                })
                .mappings(m -> m
                        .properties("id", Property.of(p -> p.long_(l -> l)))
                        .properties("name", Property.of(p -> p.text(this::textField)))
                        .properties("subtitle", Property.of(p -> p.text(this::textField)))
                        .properties("brand", Property.of(p -> p.text(t -> textField(t)
                                .fields("keyword", Property.of(k -> k.keyword(kw -> kw.ignoreAbove(256)))))))
                        .properties("category", Property.of(p -> p.text(t -> textField(t)
                                .fields("keyword", Property.of(k -> k.keyword(kw -> kw.ignoreAbove(256)))))))
                        .properties("price", Property.of(p -> p.double_(d -> d)))
                        .properties("stock", Property.of(p -> p.integer(i -> i)))
                        .properties("salesVolume", Property.of(p -> p.integer(i -> i)))
                        .properties("description", Property.of(p -> p.text(this::textField)))
                        .properties("embedding", Property.of(p -> p.denseVector(d -> d
                                .dims(EMBEDDING_DIMS)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)
                                .indexOptions(o -> o
                                        .type(DenseVectorIndexOptionsType.Hnsw)
                                        .m(16)
                                        .efConstruction(100)))))));
    }

    /** 文本字段：写入细切，查询粗切。 */
    private TextProperty.Builder textField(TextProperty.Builder builder) {
        return builder.analyzer(TEXT_ANALYZER).searchAnalyzer(SEARCH_ANALYZER);
    }
}
