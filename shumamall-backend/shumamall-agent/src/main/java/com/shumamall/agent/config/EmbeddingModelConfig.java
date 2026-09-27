package com.shumamall.agent.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量模型（Embedding）配置。
 * <p>
 * <b>为什么要单独配置：</b>chat 模型与 embedding 模型是两类模型，DeepSeek 只提供 chat
 * （{@code /v1/chat/completions}），没有 {@code /v1/embeddings} 端点。因此这里为 embedding
 * 单独建一个指向第三方 OpenAI 兼容服务的 {@link OpenAiEmbeddingModel}，与 chat 的
 * {@code spring.ai.openai.*} 配置互不干扰。
 * <p>
 * 未配置 {@code shumamall.agent.search.embedding.base-url} 时不注册该 Bean，
 * {@code ProductEmbeddingService} 自动降级为确定性伪向量（保证 Agent 主链路不崩）。
 */
@Configuration
public class EmbeddingModelConfig {

    /**
     * 商品检索用的向量模型（配置了 embedding 服务地址才生效）。
     * <p>
     * 标注 {@link Primary} 以覆盖 Spring AI 自动配置的、指向 chat 服务（DeepSeek）的向量模型。
     *
     * @param baseUrl embedding 服务的 OpenAI 兼容地址，如 https://api.siliconflow.cn
     * @param apiKey  embedding 服务的密钥
     * @param model   模型名，如 BAAI/bge-large-zh-v1.5（输出需为 1024 维）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "shumamall.agent.search.embedding", name = "base-url")
    public EmbeddingModel productEmbeddingModel(
            @Value("${shumamall.agent.search.embedding.base-url}") String baseUrl,
            @Value("${shumamall.agent.search.embedding.api-key:}") String apiKey,
            @Value("${shumamall.agent.search.embedding.model}") String model) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        // 不显式传 dimensions：部分服务商（bge 系列）不支持该参数，模型自身维度即输出维度
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }
}
