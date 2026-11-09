package com.shumamall.agent.controller;

import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import com.shumamall.agent.search.service.ProductHybridSearchService;
import com.shumamall.embedding.ProductEmbeddingService;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid 检索验证接口（索引灌库由 search 服务负责）。
 */
@Tag(name = "智能体-检索验证", description = "对合并商品索引执行 Hybrid Search（BM25 + KNN + RRF）")
@RestController
@RequestMapping("/api/v1/agent/search-index")
@RequiredArgsConstructor
public class SearchIndexController {

    private final ProductHybridSearchService hybridSearchService;
    private final ProductEmbeddingService embeddingService;

    @Operation(summary = "直接执行 Hybrid Search（BM25 + KNN + RRF）")
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
                .map(this::toItem)
                .toList();
        return R.ok(items);
    }

    private Map<String, Object> toItem(ProductDoc doc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", doc.getId());
        item.put("name", doc.getName());
        item.put("subtitle", doc.getSubtitle());
        item.put("brand", doc.getBrand());
        item.put("category", doc.getCategory());
        item.put("price", doc.getPrice());
        item.put("salesVolume", doc.getSalesVolume());
        return item;
    }
}
