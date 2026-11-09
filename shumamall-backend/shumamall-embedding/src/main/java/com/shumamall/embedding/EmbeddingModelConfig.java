package com.shumamall.embedding;

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
 * 商品检索用 embedding 模型（OpenAI 兼容端点），与 chat 模型配置分离。
 */
@Configuration
public class EmbeddingModelConfig {

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
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }
}
