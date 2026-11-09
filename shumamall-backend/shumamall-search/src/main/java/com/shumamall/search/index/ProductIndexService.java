package com.shumamall.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import com.shumamall.common.search.ProductSearchIndexNames;
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
 * 商品搜索索引管理。
 * <p>
 * 对外名字 {@code shumamall_product_search} 是别名。全量重建写入带版本后缀的物理索引，
 * 灌完后原子切换别名。查询与增量默认打别名，因此切换前旧索引仍可搜索。
 */
@Slf4j
@Service
public class ProductIndexService {

    /** 对外别名。查询、增量都使用这个名字。 */
    public static final String PRODUCT_INDEX = ProductSearchIndexNames.PRODUCT_INDEX;

    /** 语义向量维度（与 {@link com.shumamall.embedding.ProductEmbeddingService} 配置一致） */
    public static final int EMBEDDING_DIMS = 1024;

    /** 写入分析器：html_strip + ik_max_word（与 agent 侧命名一致） */
    public static final String TEXT_ANALYZER = "shumamall_text";

    /**
     * 查询分析器。IK 插件自带该分析器，mapping 可直接引用，不必在索引 settings 里再注册。
     * 粗切后的词能在细切倒排里找到，又不会把用户输入拆成碎词去匹配。
     */
    public static final String SEARCH_ANALYZER = "ik_smart";

    /** 线上搜索使用的 refresh 间隔。 */
    public static final String REFRESH_NORMAL = "1s";

    /** 灌库期间关闭自动 refresh，避免每条 bulk 都打开新 segment。 */
    public static final String REFRESH_BULK = "-1";

    /** 写入用的中文分词器（依赖 ES 安装 analysis-ik 插件） */
    private static final String TOKENIZER_IK = "ik_max_word";

    /**
     * 仅商品名称 {@code name} 使用的子串子字段：standard + lowercase + ngram。
     * 索引侧切出连续子串；查询侧仅用 standard+lowercase，使「mac」「book」等子串可命中英文名。
     */
    private static final String FILTER_NAME_NGRAM = "shumamall_name_ngram_filter";
    private static final String NAME_NGRAM_INDEX_ANALYZER = "shumamall_name_ngram_index";
    private static final String NAME_NGRAM_SEARCH_ANALYZER = "shumamall_name_ngram_search";

    private final ElasticsearchClient esClient;

    public ProductIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 新建一轮重建使用的物理索引名。
     */
    public String newPhysicalName() {
        return PRODUCT_INDEX + "_v" + System.currentTimeMillis();
    }

    /**
     * 创建物理索引。刷新间隔由调用方指定：灌库用 {@code -1}，直接对外提供搜索用 {@code 1s}。
     *
     * @return true 表示创建成功
     */
    public boolean createPhysicalIndex(String physicalIndex, String refreshInterval) {
        try {
            CreateIndexRequest request = buildCreateRequest(physicalIndex, refreshInterval);
            boolean acknowledged = esClient.indices().create(request).acknowledged();
            log.info("商品物理索引创建完成: index={}, refresh={}, acknowledged={}",
                    physicalIndex, refreshInterval, acknowledged);
            return acknowledged;
        } catch (Exception e) {
            log.error("商品物理索引创建失败: index={}, err={}", physicalIndex, e.getMessage());
            return false;
        }
    }

    /**
     * 把别名切到新物理索引。
     * <p>
     * 若别名这个名字当前仍是物理索引（升级前的旧数据），先删掉该物理索引再挂别名。
     * 这一步是首次迁移仅有的短窗口；之后的重建只改别名指向，不再删除正在服务的索引。
     * 上一代物理索引保留，更早的版本删除，避免磁盘只增不减。
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

    /**
     * 调整单个物理索引的 refresh_interval。
     */
    public void putRefreshInterval(String physicalIndex, String refreshInterval) throws IOException {
        esClient.indices().putSettings(p -> p
                .index(physicalIndex)
                .settings(s -> s.refreshInterval(Time.of(t -> t.time(refreshInterval)))));
        log.info("已调整 refresh_interval: index={}, value={}", physicalIndex, refreshInterval);
    }

    /**
     * 打开新 segment，使刚灌入的文档在别名切换后立刻可搜。
     */
    public void refresh(String physicalIndex) throws IOException {
        esClient.indices().refresh(r -> r.index(physicalIndex));
    }

    /**
     * 删除重建失败留下的物理索引。别名不变。
     */
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

    /**
     * 别名当前指向的物理索引。名字本身是物理索引、或尚不存在时返回空列表。
     */
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

    /**
     * 别名或同名物理索引是否存在。
     */
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
                    // ES 默认 max_ngram_diff=1；name.ngram 使用 ngram(2–12)，须放宽至至少 10。
                    s.index(i -> i.maxNgramDiff(10));
                    s.analysis(a -> a
                            .analyzer(TEXT_ANALYZER, an -> an
                                    .custom(c -> c
                                            .tokenizer(TOKENIZER_IK)
                                            .charFilter("html_strip")))
                            .filter(FILTER_NAME_NGRAM, f -> f
                                    .definition(d -> d.ngram(n -> n
                                            .minGram(2)
                                            .maxGram(12))))
                            .analyzer(NAME_NGRAM_INDEX_ANALYZER, an -> an
                                    .custom(c -> c
                                            .tokenizer("standard")
                                            .filter("lowercase")
                                            .filter(FILTER_NAME_NGRAM)))
                            .analyzer(NAME_NGRAM_SEARCH_ANALYZER, an -> an
                                    .custom(c -> c
                                            .tokenizer("standard")
                                            .filter("lowercase"))));
                    if (refreshInterval != null) {
                        s.refreshInterval(Time.of(t -> t.time(refreshInterval)));
                    }
                    return s;
                })
                .mappings(m -> m
                        .properties("id", Property.of(p -> p.long_(l -> l)))
                        .properties("name", Property.of(p -> p.text(this::textFieldNameForSearch)))
                        .properties("subtitle", Property.of(p -> p.text(this::textField)))
                        .properties("description", Property.of(p -> p.text(this::textField)))
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
                                .format("strict_date_optional_time||epoch_millis"))))
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

    /**
     * 商品名称：主字段走 IK；子字段 {@code ngram} 承担英文/数字子串召回（不设 prefix 子字段）。
     */
    private TextProperty.Builder textFieldNameForSearch(TextProperty.Builder builder) {
        return textField(builder).fields("ngram", Property.of(f -> f.text(t -> t
                .analyzer(NAME_NGRAM_INDEX_ANALYZER)
                .searchAnalyzer(NAME_NGRAM_SEARCH_ANALYZER))));
    }
}
