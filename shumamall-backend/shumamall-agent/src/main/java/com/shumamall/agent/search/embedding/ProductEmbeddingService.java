package com.shumamall.agent.search.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 商品文本向量化服务。
 * <p>
 * 封装 Spring AI {@link EmbeddingModel}（OpenAI 兼容 embedding 服务，由
 * {@code EmbeddingModelConfig} 单独配置，与 DeepSeek chat 模型分离），
 * 为商品文本（名称 + 副标题 + 品牌 + 分类）生成语义向量，供 {@code ProductIndexWriter} 灌库、
 * {@code HybridSearchTool} 检索时向量化 query 使用。
 * <p>
 * 未配置 embedding 服务（或调用失败）时退化为<b>确定性伪向量</b>：以文本 hashCode 为随机种子
 * 生成固定维度向量，保证不依赖外部模型也能跑通"灌库 → 检索"链路演示（相同文本始终得到相同向量）。
 * 注意伪向量只保证链路可跑，不具备语义相似能力。
 */
@Slf4j
@Service
public class ProductEmbeddingService {

    /** 向量维度（与 ES 索引 mapping 的 dense_vector dims 一致） */
    private final int dims;
    private final boolean mockLlm;
    private final EmbeddingModel embeddingModel;
    /** 检索侧 query 指令前缀（bge 系列官方用法：query 加、文档不加） */
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

    /**
     * 生成文档侧文本向量（灌库用，不加指令前缀）。
     *
     * @param text 待向量化文本
     * @return dims 维向量
     */
    public List<Float> embed(String text) {
        if (mockLlm || embeddingModel == null) {
            return deterministicVector(text);
        }
        try {
            // Spring AI 1.1.8 embed(String) 返回 float[]
            float[] floats = embeddingModel.embed(text);
            if (floats == null || floats.length == 0) {
                return deterministicVector(text);
            }
            if (floats.length != dims) {
                // 维度与索引 mapping 不一致时 ES 会拒绝写入，这里给出明确原因
                log.error("向量维度与配置不一致: 模型返回={}, 配置 dims={}。"
                                + "请将 shumamall.agent.search.embedding.dims 改为模型实际维度并重建索引",
                        floats.length, dims);
            }
            List<Float> vector = new ArrayList<>(floats.length);
            for (float v : floats) {
                vector.add(v);
            }
            return vector;
        } catch (Exception e) {
            log.warn("向量化失败，退化为确定性伪向量: len={}, text=[{}], err={}",
                    text == null ? -1 : text.length(), text, e.getMessage());
            return deterministicVector(text);
        }
    }

    /**
     * 生成检索 query 向量（检索用，按配置追加指令前缀）。
     * <p>
     * bge 系列官方用法要求检索侧 query 加指令、文档侧不加，实测可提升语义区分度。
     * mock 模式（伪向量）下前缀无意义，直接返回 {@link #embed(String)}，避免破坏同文本可复现性。
     *
     * @param text 用户检索描述，如"3000 块拍照好的手机"
     * @return dims 维向量
     */
    public List<Float> embedQuery(String text) {
        if (mockLlm || embeddingModel == null
                || queryPrefix == null || queryPrefix.isBlank() || text == null) {
            return embed(text);
        }
        return embed(queryPrefix + text);
    }

    /**
     * 确定性伪向量：以文本 hash 为随机种子，保证同文本同向量、可复现。
     */
    private List<Float> deterministicVector(String text) {
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Float> vector = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) {
            vector.add(random.nextFloat());
        }
        return vector;
    }
}
