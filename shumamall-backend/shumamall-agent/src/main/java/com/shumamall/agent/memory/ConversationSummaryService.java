package com.shumamall.agent.memory;

import com.shumamall.agent.config.AgentPromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 调用大模型将会话片段合并进滚动摘要；不挂 Memory Advisor，避免递归读历史。
 */
@Slf4j
@Service
public class ConversationSummaryService {

    private final ChatClient summaryChatClient;
    private final AgentPromptProvider promptProvider;
    private final boolean mockLlm;

    public ConversationSummaryService(@Qualifier("summaryChatClient") ChatClient summaryChatClient,
                                      AgentPromptProvider promptProvider,
                                      @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm) {
        this.summaryChatClient = summaryChatClient;
        this.promptProvider = promptProvider;
        this.mockLlm = mockLlm;
    }

    /**
     * @param existingSummary 已有摘要，可为空
     * @param toMerge         即将从原文中移除、需要并入摘要的消息（按时间顺序）
     */
    public String mergeSummary(String existingSummary, List<Message> toMerge) {
        if (toMerge == null || toMerge.isEmpty()) {
            return existingSummary == null ? "" : existingSummary;
        }
        if (mockLlm) {
            return mockMerge(existingSummary, toMerge);
        }
        String fragment = formatMessages(toMerge);
        String prior = StringUtils.hasText(existingSummary) ? existingSummary : "（无）";
        try {
            String merged = summaryChatClient.prompt()
                    .user(promptProvider.summaryUserTemplate(prior, fragment))
                    .call()
                    .content();
            return merged == null ? prior : merged.strip();
        } catch (Exception e) {
            log.warn("会话摘要 LLM 调用失败，保留原摘要: err={}", e.getMessage());
            return existingSummary == null ? "" : existingSummary;
        }
    }

    private static String mockMerge(String existingSummary, List<Message> toMerge) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(existingSummary)) {
            sb.append(existingSummary).append(' ');
        }
        sb.append(formatMessages(toMerge));
        String text = sb.toString().strip();
        if (text.length() > 800) {
            return text.substring(0, 800) + "…";
        }
        return text;
    }

    static String formatMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            sb.append(message.getMessageType().name())
                    .append(": ")
                    .append(message.getText())
                    .append('\n');
        }
        return sb.toString().strip();
    }
}
