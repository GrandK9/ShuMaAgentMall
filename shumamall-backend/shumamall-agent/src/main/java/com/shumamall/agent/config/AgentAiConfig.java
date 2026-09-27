package com.shumamall.agent.config;

import com.shumamall.agent.tools.AddressUserTool;
import com.shumamall.agent.tools.CartTool;
import com.shumamall.agent.tools.HybridSearchTool;
import com.shumamall.agent.tools.OrderTool;
import com.shumamall.agent.tools.ProductSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 服务 AI 组件装配。
 * <p>
 * 显式声明 {@link ChatClient} Bean：由 Spring AI 自动配置的
 * {@link ChatClient.Builder} 构建（其底层模型由 OpenAiChatModel 或
 * MockChatModel 按 {@code shumamall.agent.mock-llm} 开关提供）。
 * <p>
 * {@link ChatMemory}：基于 {@link RedisChatMemoryRepository} 的滑动窗口记忆，
 * 由 {@link MessageChatMemoryAdvisor} 在每请求注入/写回（会话 ID 经 advisor param 透传）。
 * <p>
 * {@link ToolCallbackProvider}：把 {@code @Tool} 工具类统一注册为框架的
 * ToolCallback，供规划模块的 {@link ToolCatalog} 消费。
 * <p>
 * ChatClient 为不可变组件，每个技能的差异化系统提示词 / 记忆 Advisor
 * 通过 {@link ChatClient#mutate()} 派生，不在全局 Bean 上做改动。
 */
@Configuration
public class AgentAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository,
                                 @Value("${shumamall.agent.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public ToolCallbackProvider agentToolProvider(ProductSearchTool productSearchTool,
                                                  HybridSearchTool hybridSearchTool,
                                                  CartTool cartTool,
                                                  OrderTool orderTool,
                                                  AddressUserTool addressUserTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productSearchTool, hybridSearchTool, cartTool, orderTool, addressUserTool)
                .build();
    }
}
