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
import org.springframework.context.annotation.Primary;

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
 * {@link ToolCallbackProvider}：全量工具供规划模块的 {@link com.shumamall.agent.planning.ToolCatalog}；
 * 导购另注册一份只读工具，交给 ChatClient 的 tools 字段做原生 function calling。
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
    @Primary
    public ToolCallbackProvider agentToolProvider(ProductSearchTool productSearchTool,
                                                  HybridSearchTool hybridSearchTool,
                                                  CartTool cartTool,
                                                  OrderTool orderTool,
                                                  AddressUserTool addressUserTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productSearchTool, hybridSearchTool, cartTool, orderTool, addressUserTool)
                .build();
    }

    /**
     * 导购原生工具循环只挂只读检索，避免模型在 function calling 里直接下单或加购。
     */
    @Bean
    public ToolCallbackProvider shoppingGuideToolProvider(ProductSearchTool productSearchTool,
                                                          HybridSearchTool hybridSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productSearchTool, hybridSearchTool)
                .build();
    }
}
