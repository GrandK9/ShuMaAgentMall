package com.shumamall.agent.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Mock 模型配置（兜底链路）。
 * <p>
 * 当 {@code shumamall.agent.mock-llm=true} 时启用：以本地回显模型替换
 * OpenAiChatModel（DeepSeek），使技能路由 / 规划 / 记忆 / 工具调用全链路
 * 在<b>不依赖真实 LLM</b>的情况下也能跑通演示。默认 false，走真实 DeepSeek。
 */
@Configuration
@ConditionalOnProperty(name = "shumamall.agent.mock-llm", havingValue = "true")
public class MockChatModelConfig {

    /**
     * 声明 Mock 模型为 @Primary，覆盖 Spring AI 自动配置的 OpenAI 模型。
     */
    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new MockChatModel();
    }

    /**
     * 简化 Mock 模型：回显最后一条用户消息。
     * <p>
     * 仅实现 {@link ChatModel} 的最小契约（call + stream），
     * 用于验证"技能 → 记忆 → 规划 → 工具调用 → 回复"链路完整性。
     */
    static class MockChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            String text = prompt.getInstructions().stream()
                    .filter(m -> m.getMessageType() == MessageType.USER)
                    .reduce((first, second) -> second)
                    .map(Message::getText)
                    .orElse("");
            AssistantMessage reply = new AssistantMessage("【Mock 模型】已收到您的消息：\n" + text);
            return ChatResponse.builder()
                    .generations(List.of(new Generation(reply)))
                    .build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
