package com.shumamall.agent.memory;

import com.shumamall.agent.dto.ChatSessionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文管理服务。
 * <p>
 * 负责会话生命周期：创建会话、校验归属、统计轮次。
 * 实际消息存取委托给 {@link RedisChatMemoryRepository}，本服务面向编排器
 * 提供更高层的会话 API，并保证"同一用户会话隔离"。
 */
@Slf4j
@Service
public class ConversationService {

    private final RedisChatMemoryRepository memoryRepository;

    public ConversationService(RedisChatMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * 获取（或创建）属于当前用户的会话。
     *
     * @param sessionId 前端传入的会话 ID，为空则新建
     * @param userId    当前登录用户
     * @return 有效的会话 ID
     */
    public String getOrCreateSession(String sessionId, Long userId) {
        String effectiveSessionId = sessionId;
        if (effectiveSessionId == null || effectiveSessionId.isBlank()) {
            effectiveSessionId = RedisChatMemoryRepository.newSessionId();
            log.info("创建新会话: sessionId={}, userId={}", effectiveSessionId, userId);
        } else if (!memoryRepository.isOwner(effectiveSessionId, userId)) {
            // 会话不存在或不属于该用户 → 视为新会话（防止越权续聊他人会话）
            log.warn("会话归属校验未通过，重建会话: sessionId={}, userId={}", effectiveSessionId, userId);
            effectiveSessionId = RedisChatMemoryRepository.newSessionId();
        }
        // 续期会话 TTL（bindUser 内部重新设置过期时间）
        memoryRepository.bindUser(effectiveSessionId, userId);
        return effectiveSessionId;
    }

    /**
     * 统计会话内已累计的轮次数。
     */
    public int countRounds(String sessionId) {
        // 每轮次包含 1 条用户消息 + 1 条助手回复，除以 2 即轮数
        List<Message> history = memoryRepository.findByConversationId(sessionId);
        return history.size() / 2;
    }

    /**
     * 清除指定会话的全部记忆。
     */
    public void clearSession(String sessionId) {
        memoryRepository.deleteByConversationId(sessionId);
        log.info("清除会话记忆: sessionId={}", sessionId);
    }

    /**
     * 校验会话是否属于指定用户。
     */
    public boolean isOwner(String sessionId, Long userId) {
        return memoryRepository.isOwner(sessionId, userId);
    }

    /**
     * 查询指定会话的历史消息。
     */
    public List<Message> findHistory(String sessionId) {
        return memoryRepository.findByConversationId(sessionId);
    }

    /**
     * 查询用户的会话列表摘要。
     */
    public List<ChatSessionSummary> findSessions(Long userId) {
        List<String> sessionIds = memoryRepository.findSessionIdsByUser(userId);
        List<ChatSessionSummary> summaries = new ArrayList<>(sessionIds.size());
        for (String sessionId : sessionIds) {
            List<Message> history = memoryRepository.findByConversationId(sessionId);
            if (history == null || history.isEmpty()) {
                continue;
            }
            Message last = history.get(history.size() - 1);
            String preview = last.getText();
            if (preview.length() > 30) {
                preview = preview.substring(0, 30) + "…";
            }
            summaries.add(ChatSessionSummary.builder()
                    .sessionId(sessionId)
                    .lastMessagePreview(preview)
                    .messageCount(history.size())
                    .build());
        }
        return summaries;
    }
}
