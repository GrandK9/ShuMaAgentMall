package com.shumamall.agent.service;

import com.shumamall.agent.dto.ToolResult;
import com.shumamall.agent.tools.RecordingToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 导购技能的原生 function calling。
 * <p>
 * 只读检索工具挂到 ChatClient；流式对话用 {@link #streamTokens}（真 token + 工具结果回调），
 * 非流式 {@link #chat} 仍用 {@code call()} 一次返回。
 */
@Service
public class ShoppingGuideChat {

    private final ToolCallbackProvider shoppingGuideToolProvider;

    public ShoppingGuideChat(@Qualifier("shoppingGuideToolProvider") ToolCallbackProvider shoppingGuideToolProvider) {
        this.shoppingGuideToolProvider = shoppingGuideToolProvider;
    }

    /**
     * 非流式：跑完工具循环并返回模型最终回复。
     */
    public Outcome complete(ChatClient skillClient, String sessionId, String userMessage, Long userId) {
        List<ToolResult> toolResults = new ArrayList<>();
        ToolCallback[] callbacks = wrapCallbacks(toolResults, null);
        String reply = skillClient.prompt()
                .user(userMessage)
                .toolCallbacks(callbacks)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        return new Outcome(reply, List.copyOf(toolResults));
    }

    /**
     * 流式：订阅后由框架执行 tool 循环，每完成一次工具调用触发 {@code onToolRecorded}，正文逐 token 推送。
     */
    public Flux<String> streamTokens(ChatClient skillClient,
                                     String sessionId,
                                     String userMessage,
                                     Long userId,
                                     Consumer<ToolResult> onToolRecorded) {
        ToolCallback[] callbacks = wrapCallbacks(null, onToolRecorded);
        return skillClient.prompt()
                .user(userMessage)
                .toolCallbacks(callbacks)
                .toolContext(Map.of("userId", userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }

    private ToolCallback[] wrapCallbacks(List<ToolResult> sink, Consumer<ToolResult> onToolRecorded) {
        return Arrays.stream(shoppingGuideToolProvider.getToolCallbacks())
                .map(callback -> (ToolCallback) new RecordingToolCallback(callback, sink, onToolRecorded))
                .toArray(ToolCallback[]::new);
    }

    public record Outcome(String reply, List<ToolResult> toolResults) {
    }
}
