package com.shumamall.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的会话记忆仓储。
 * <p>
 * 实现 Spring AI 的 {@link ChatMemoryRepository} 接口，供
 * {@code MessageChatMemoryAdvisor} 在每次请求时自动注入历史消息（记忆读取），
 * 并在回复生成后自动写回（记忆写入）。
 * <ul>
 *   <li>Key 结构：{@code agent:memory:{conversationId}}，Value 为消息 JSON 数组</li>
 *   <li>TTL：会话 {@code ttl-minutes} 分钟无操作自动过期，实现"短期记忆"生命周期</li>
 *   <li>容量：单会话消息条数裁剪由 {@code MessageWindowChatMemory} 的 maxMessages 负责</li>
 * </ul>
 */
@Slf4j
@Repository
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String MEMORY_KEY_PREFIX = "agent:memory:";
    private static final String MEMBER_KEY_PREFIX = "agent:member:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${shumamall.agent.memory.ttl-minutes:30}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(MEMORY_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(key -> key.substring(MEMORY_KEY_PREFIX.length()))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return load(conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 全量覆盖保存；消息条数裁剪由 MessageWindowChatMemory（maxMessages）负责
        save(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(keyOf(conversationId));
    }

    // ==================== 会话成员绑定（防串号） ====================

    /**
     * 将会话绑定到用户：记录 {conversationId -> userId}，并在用户侧建立索引。
     */
    public void bindUser(String conversationId, Long userId) {
        redisTemplate.opsForValue().set(MEMBER_KEY_PREFIX + conversationId, String.valueOf(userId), ttl);
    }

    /**
     * 校验会话是否属于当前用户，防止越权读取他人会话记忆。
     */
    public boolean isOwner(String conversationId, Long userId) {
        String owner = redisTemplate.opsForValue().get(MEMBER_KEY_PREFIX + conversationId);
        return owner != null && owner.equals(String.valueOf(userId));
    }

    // ==================== 序列化 ====================

    @SuppressWarnings("unchecked")
    private List<Message> load(String conversationId) {
        String json = redisTemplate.opsForValue().get(keyOf(conversationId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<Message> messages = new ArrayList<>(items.size());
            for (Map<String, Object> item : items) {
                MessageType type = MessageType.valueOf(String.valueOf(item.get("type")));
                String content = String.valueOf(item.getOrDefault("content", ""));
                messages.add(switch (type) {
                    case USER -> new UserMessage(content);
                    case ASSISTANT -> new AssistantMessage(content);
                    case SYSTEM -> new SystemMessage(content);
                    default -> new UserMessage(content);
                });
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取会话记忆失败，返回空历史: conversationId={}, err={}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save(String conversationId, List<Message> messages) {
        try {
            List<Map<String, String>> items = new ArrayList<>(messages.size());
            for (Message message : messages) {
                items.add(Map.of("type", message.getMessageType().name(), "content", message.getText()));
            }
            redisTemplate.opsForValue().set(keyOf(conversationId), objectMapper.writeValueAsString(items), ttl);
        } catch (Exception e) {
            log.error("写入会话记忆失败: conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    private String keyOf(String conversationId) {
        return MEMORY_KEY_PREFIX + conversationId;
    }

    /**
     * 查询指定用户拥有的所有会话 ID。
     * <p>
     * 通过扫描 agent:member:* 键并校验归属实现。会话量不大时可用，
     * 生产高并发下建议维护 userId -> sessionIds 的反向索引。
     */
    public List<String> findSessionIdsByUser(Long userId) {
        Set<String> keys = redisTemplate.keys(MEMBER_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        String uid = String.valueOf(userId);
        return keys.stream()
                .filter(key -> uid.equals(redisTemplate.opsForValue().get(key)))
                .map(key -> key.substring(MEMBER_KEY_PREFIX.length()))
                .collect(Collectors.toList());
    }

    /**
     * 生成新的会话 ID。
     */
    public static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
