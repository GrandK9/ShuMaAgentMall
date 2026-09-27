package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品 Hybrid Search 服务。
 * <p>
 * 一次检索同时下发 BM25 关键词检索（multi_match）与 KNN 向量语义检索，
 * 再用 <b>RRF（Reciprocal Rank Fusion，倒数排名融合）</b>按排名合并两路结果：
 *
 * <pre>{@code
 * score(d) = Σ  1 / (rankConstant + rank_i(d))
 *            i
 * }</pre>
 *
 * rankConstant（默认 60）越大，靠前名次的相对优势越平滑，避免单路榜首独大。
 * 该方案解决纯关键词检索召回不全、纯向量检索对精确词（型号、编号）不敏感的问题。
 * <p>
 * <b>为什么在应用层做融合，而不是用 ES 原生 RRF：</b>
 * ES 的 {@code rank: rrf} retriever 需 Platinum/Enterprise 授权，Basic（开源免费）版会报
 * {@code current license is non-compliant for [Reciprocal Rank Fusion (RRF)]}；
 * 而两路检索本身（multi_match 与 knn）在 Basic 版均可正常使用。
 * 因此在应用层融合：不依赖授权，融合过程对两路召回分别可见，便于排查召回质量。
 *
 * <pre>{@code
 * request: { keyword: "3000块拍照好的手机", queryVector: [0.1, 0.2, ...] }
 *   ├── BM25 路: multi_match(name^3, subtitle^2, brand, category, description), size=windowSize
 *   ├── KNN  路: knn(embedding, k=windowSize, numCandidates), size=windowSize
 *   └── RRF 融合: score = 1/(rankConstant + rank_bm25) + 1/(rankConstant + rank_knn)
 * }</pre>
 */
@Slf4j
@Service
public class ProductHybridSearchService {

    /** 商品索引名（与 ProductIndexService 保持一致） */
    public static final String PRODUCT_INDEX = "shumamall_product";

    /** BM25 关键词检索字段（^n 为字段权重提升，品牌/分类用精确匹配字段提升可信度） */
    private static final List<String> TEXT_FIELDS = List.of("name^3", "subtitle^2", "brand", "category", "description");

    /** 语义向量字段（对应索引 mapping 的 dense_vector） */
    private static final String EMBEDDING_FIELD = "embedding";

    /** 融合后无 size 时的兜底返回条数 */
    private static final int DEFAULT_SIZE = 5;

    private final ElasticsearchClient esClient;

    /** RRF 排名平滑常数（越大越平滑） */
    private final int rankConstant;

    /** 单路检索参与的候选窗口大小（融合前每路取前 N 条） */
    private final int windowSize;

    public ProductHybridSearchService(ElasticsearchClient esClient,
                                      @Value("${shumamall.agent.search.rrf.rank-constant:60}") int rankConstant,
                                      @Value("${shumamall.agent.search.rrf.window-size:50}") int windowSize) {
        this.esClient = esClient;
        this.rankConstant = rankConstant;
        this.windowSize = windowSize;
    }

    /**
     * Hybrid Search：BM25 + KNN 两路召回，RRF 融合后返回排序结果。
     * <p>
     * ES 不可用/查询失败时返回空列表并记录错误，不影响 Agent 主链路
     * （调用方可按空结果降级到关键词规则或追问用户）。
     */
    public List<ProductDoc> search(HybridSearchRequest request) {
        try {
            List<ProductDoc> bm25Hits = searchBm25(request);
            List<ProductDoc> knnHits = searchKnn(request);
            List<ProductDoc> fused = rrfFuse(List.of(bm25Hits, knnHits), resolveSize(request));
            log.debug("Hybrid Search 完成: keyword={}, bm25={}, knn={}, fused={}",
                    request.getKeyword(), bm25Hits.size(), knnHits.size(), fused.size());
            return fused;
        } catch (Exception e) {
            log.error("Hybrid Search 失败: keyword={}, err={}", request.getKeyword(), e.getMessage());
            return List.of();
        }
    }

    /**
     * BM25 关键词路检索。
     */
    private List<ProductDoc> searchBm25(HybridSearchRequest request) throws Exception {
        SearchResponse<ProductDoc> resp = esClient.search(buildBm25Request(request), ProductDoc.class);
        return toDocs(resp);
    }

    /**
     * KNN 向量语义路检索。
     */
    private List<ProductDoc> searchKnn(HybridSearchRequest request) throws Exception {
        SearchResponse<ProductDoc> resp = esClient.search(buildKnnRequest(request), ProductDoc.class);
        return toDocs(resp);
    }

    /**
     * 构建 BM25 路查询（包级可见，便于单测直接断言查询结构）。
     */
    SearchRequest buildBm25Request(HybridSearchRequest request) {
        String keyword = request.getKeyword();
        return SearchRequest.of(b -> b
                .index(PRODUCT_INDEX)
                .size(windowSize)
                .query(q -> {
                    if (keyword == null || keyword.isBlank()) {
                        // 无关键词时该路退化为全量匹配，实际排序交由向量路主导
                        return q.matchAll(m -> m);
                    }
                    return q.multiMatch(m -> m
                            .fields(TEXT_FIELDS)
                            .query(keyword));
                }));
    }

    /**
     * 构建 KNN 路查询（包级可见，便于单测直接断言查询结构）。
     */
    SearchRequest buildKnnRequest(HybridSearchRequest request) {
        int candidates = Math.max(request.getNumCandidates(), windowSize);
        return SearchRequest.of(b -> b
                .index(PRODUCT_INDEX)
                .size(windowSize)
                .knn(k -> k
                        .field(EMBEDDING_FIELD)
                        .queryVector(request.getQueryVector())
                        .k(windowSize)
                        .numCandidates(candidates)));
    }

    /**
     * RRF 融合：对每路的排名取倒数累加，按融合分降序返回前 size 条。
     *
     * @param rankedLists 各路已排序结果（索引 0 为第 1 名）
     * @param size        融合后返回条数
     */
    List<ProductDoc> rrfFuse(List<List<ProductDoc>> rankedLists, int size) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, ProductDoc> docs = new LinkedHashMap<>();
        for (List<ProductDoc> ranked : rankedLists) {
            for (int i = 0; i < ranked.size(); i++) {
                ProductDoc doc = ranked.get(i);
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                int rank = i + 1;
                scores.merge(doc.getId(), 1.0 / (rankConstant + rank), Double::sum);
                docs.putIfAbsent(doc.getId(), doc);
            }
        }
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        return sorted.stream()
                .limit(size)
                .map(entry -> docs.get(entry.getKey()))
                .toList();
    }

    /**
     * 融合后返回条数：优先 size，其次 topK，最后取默认值。
     */
    private int resolveSize(HybridSearchRequest request) {
        if (request.getSize() > 0) {
            return request.getSize();
        }
        if (request.getTopK() > 0) {
            return request.getTopK();
        }
        return DEFAULT_SIZE;
    }

    /**
     * 提取命中文档（过滤 source 为空的情况）。
     */
    private List<ProductDoc> toDocs(SearchResponse<ProductDoc> resp) {
        if (resp.hits() == null || resp.hits().hits() == null) {
            return List.of();
        }
        return resp.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }
}
