package com.shumamall.agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口记忆：原文超轮数阈值时由大模型合并摘要（每轮 add 至多压缩一次，不做循环）。
 */
public class SummarizingMessageWindowChatMemory implements ChatMemory {

    static final String SUMMARY_SYSTEM_PREFIX = "以下是本对话较早内容的摘要，供理解上下文：\n";

    private final RedisChatMemoryRepository repository;
    private final ConversationSummaryService summaryService;
    private final int maxMessages;
    private final int compressAfterRounds;
    private final int keepRecentMessages;
    private final boolean summarizeEnabled;

    public SummarizingMessageWindowChatMemory(RedisChatMemoryRepository repository,
                                              ConversationSummaryService summaryService,
                                              int maxMessages,
                                              int compressAfterRounds,
                                              int keepRecentMessages,
                                              boolean summarizeEnabled) {
        this.repository = repository;
        this.summaryService = summaryService;
        this.maxMessages = Math.max(2, maxMessages);
        this.compressAfterRounds = Math.max(1, compressAfterRounds);
        this.keepRecentMessages = Math.max(2, keepRecentMessages);
        this.summarizeEnabled = summarizeEnabled;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        MemoryEnvelope envelope = repository.loadEnvelope(conversationId);
        envelope.getMessages().addAll(messages);
        envelope.syncRoundCountFromMessages();
        trimToMaxMessages(envelope);
        maybeCompressOnce(envelope);
        trimToMaxMessages(envelope);
        envelope.syncRoundCountFromMessages();
        repository.saveEnvelope(conversationId, envelope);
    }

    @Override
    public List<Message> get(String conversationId) {
        MemoryEnvelope envelope = repository.loadEnvelope(conversationId);
        List<Message> result = new ArrayList<>();
        if (StringUtils.hasText(envelope.getSummary())) {
            result.add(new SystemMessage(SUMMARY_SYSTEM_PREFIX + envelope.getSummary()));
        }
        result.addAll(envelope.getMessages());
        return result;
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    /**
     * 供规划/路由使用：摘要 + 近期原文，不含注入用的 System 包装。
     */
    public MemoryEnvelope loadRawEnvelope(String conversationId) {
        return repository.loadEnvelope(conversationId);
    }

    private void maybeCompressOnce(MemoryEnvelope envelope) {
        if (!summarizeEnabled || envelope.getRoundCount() <= compressAfterRounds) {
            return;
        }
        List<Message> all = envelope.getMessages();
        if (all.size() <= keepRecentMessages) {
            return;
        }
        int compressCount = all.size() - keepRecentMessages;
        List<Message> toMerge = new ArrayList<>(all.subList(0, compressCount));
        String merged = summaryService.mergeSummary(envelope.getSummary(), toMerge);
        envelope.setSummary(merged);
        all.subList(0, compressCount).clear();
    }

    private void trimToMaxMessages(MemoryEnvelope envelope) {
        List<Message> all = envelope.getMessages();
        if (all.size() <= maxMessages) {
            return;
        }
        int drop = all.size() - maxMessages;
        all.subList(0, drop).clear();
    }
}
