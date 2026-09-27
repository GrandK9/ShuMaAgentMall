package com.shumamall.agent.search.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Hybrid Search 请求参数。
 * <p>
 * keyword 走 BM25 关键词检索，queryVector 走 KNN 向量语义检索，
 * 两路结果在应用层用 RRF（倒数排名融合）合并排序
 * —— 不用 ES 原生 {@code rank: rrf}，因为它是 Platinum 授权特性，Basic 版会报 license 不合规，
 * 详见 {@code ProductHybridSearchService}。
 */
@Getter
@Builder
public class HybridSearchRequest {

    /** 关键词（BM25 检索用，可为空：空时退化为 match_all） */
    private String keyword;

    /** 语义向量（KNN 检索用，来源为 query 文本的 embedding） */
    private List<Float> queryVector;

    /** KNN 返回的近邻数量 */
    @Builder.Default
    private int topK = 10;

    /** KNN 候选集大小（越大召回越准、越慢） */
    @Builder.Default
    private int numCandidates = 50;

    /** 最终返回条数 */
    @Builder.Default
    private int size = 10;
}
