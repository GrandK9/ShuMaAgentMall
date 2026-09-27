package com.shumamall.agent.controller;

import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.agent.search.service.ProductHybridSearchService;
import com.shumamall.agent.search.service.ProductIndexService;
import com.shumamall.agent.search.service.ProductIndexWriter;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品检索索引运维接口。
 * <p>
 * 供初始化与验证使用：
 * <ul>
 *   <li>{@code POST /rebuild}：创建（或重建）索引并全量灌库，打通"灌库 → 检索"链路</li>
 *   <li>{@code GET /search}：跳过 LLM，直接执行 Hybrid Search，便于单独验证检索效果</li>
 * </ul>
 * 路径位于 {@code /api/v1/agent/*} 下，同样受 TokenFilter 保护。
 */
@Tag(name = "智能体-检索索引运维", description = "商品检索索引的创建灌库与 Hybrid Search 验证接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/search-index")
public class SearchIndexController {

    private final ProductIndexService indexService;
    private final ProductIndexWriter indexWriter;
    private final ProductHybridSearchService hybridSearchService;
    private final ProductEmbeddingService embeddingService;

    public SearchIndexController(ProductIndexService indexService,
                                 ProductIndexWriter indexWriter,
                                 ProductHybridSearchService hybridSearchService,
                                 ProductEmbeddingService embeddingService) {
        this.indexService = indexService;
        this.indexWriter = indexWriter;
        this.hybridSearchService = hybridSearchService;
        this.embeddingService = embeddingService;
    }

    /**
     * 创建索引并全量灌库。
     *
     * @param recreate 是否先删除已有索引再重建（mapping / 分词器变更时必须传 true）
     */
    @Operation(summary = "创建索引并全量灌库")
    @PostMapping("/rebuild")
    public R<Map<String, Object>> rebuild(
            @Parameter(description = "是否先删除已有索引再重建，mapping/分词器变更时必须传 true")
            @RequestParam(value = "recreate", defaultValue = "false") boolean recreate) {
        boolean created = indexService.createIndex(recreate);
        int written = indexWriter.indexAllProducts();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indexCreated", created);
        result.put("documentCount", written);
        log.info("索引重建完成: created={}, written={}", created, written);
        return R.ok(result);
    }

    /**
     * 直接执行 Hybrid Search（BM25 + KNN + RRF），返回命中的商品。
     */
    @Operation(summary = "直接执行 Hybrid Search（BM25 + KNN + RRF），返回命中的商品")
    @GetMapping("/search")
    public R<List<Map<String, Object>>> search(
            @Parameter(description = "搜索关键词") @RequestParam("keyword") String keyword,
            @Parameter(description = "返回条数，1-20，默认 5")
            @RequestParam(value = "size", defaultValue = "5") int size) {
        int limit = Math.min(Math.max(size, 1), 20);
        List<Float> queryVector = embeddingService.embedQuery(keyword);
        HybridSearchRequest request = HybridSearchRequest.builder()
                .keyword(keyword)
                .queryVector(queryVector)
                .topK(limit)
                .numCandidates(limit * 5)
                .size(limit)
                .build();

        List<Map<String, Object>> items = hybridSearchService.search(request).stream()
                .<Map<String, Object>>map(doc -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", doc.getId());
                    item.put("name", doc.getName());
                    item.put("subtitle", doc.getSubtitle());
                    item.put("brand", doc.getBrand());
                    item.put("category", doc.getCategory());
                    item.put("price", doc.getPrice());
                    item.put("salesVolume", doc.getSalesVolume());
                    return item;
                })
                .toList();
        return R.ok(items);
    }
}
