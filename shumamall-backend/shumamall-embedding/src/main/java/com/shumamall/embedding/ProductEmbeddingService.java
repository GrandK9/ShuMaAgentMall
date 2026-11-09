package com.shumamall.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 商品 / 检索文本向量化（search 灌库、agent Hybrid 检索共用）。
 */
@Slf4j
@Service
public class ProductEmbeddingService {

    private final int dims;
    private final boolean mockLlm;
    private final EmbeddingModel embeddingModel;
    private final String queryPrefix;

    public ProductEmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel,
                                   @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm,
                                   @Value("${shumamall.agent.search.embedding.dims:1024}") int dims,
                                   @Value("${shumamall.agent.search.embedding.query-prefix:}") String queryPrefix) {
        this.embeddingModel = embeddingModel;
        this.mockLlm = mockLlm;
        this.dims = dims;
        this.queryPrefix = queryPrefix;
    }

    public int dims() {
        return dims;
    }

    /** 文档侧向量（灌库，不加 query 前缀）。 */
    public List<Float> embed(String text) {
        if (mockLlm || embeddingModel == null) {
            return deterministicVector(text);
        }
        try {
            float[] floats = embeddingModel.embed(text);
            if (floats == null || floats.length == 0) {
                return deterministicVector(text);
            }
            if (floats.length != dims) {
                log.error("向量维度与配置不一致: 模型返回={}, 配置 dims={}",
                        floats.length, dims);
            }
            List<Float> vector = new ArrayList<>(floats.length);
            for (float v : floats) {
                vector.add(v);
            }
            return vector;
        } catch (Exception e) {
            log.warn("向量化失败，退化为确定性伪向量: len={}, err={}",
                    text == null ? -1 : text.length(), e.getMessage());
            return deterministicVector(text);
        }
    }

    /** 检索 query 向量（按配置追加 bge 类 query 前缀）。 */
    public List<Float> embedQuery(String text) {
        if (mockLlm || embeddingModel == null
                || queryPrefix == null || queryPrefix.isBlank() || text == null) {
            return embed(text);
        }
        return embed(queryPrefix + text);
    }

    private List<Float> deterministicVector(String text) {
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Float> vector = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) {
            vector.add(random.nextFloat());
        }
        return vector;
    }
}
