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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的会话记忆仓储。
 * <p>
 * Key：{@code agent:memory:{conversationId}}。Value 为 v1 结构（summary + roundCount + messages），
 * 兼容旧版纯消息 JSON 数组。
 */
@Slf4j
@Repository
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String MEMORY_KEY_PREFIX = "agent:memory:";
    private static final String MEMBER_KEY_PREFIX = "agent:member:";
    private static final int SCHEMA_VERSION = 1;

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
        return new ArrayList<>(loadEnvelope(conversationId).getMessages());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        MemoryEnvelope envelope = loadEnvelope(conversationId);
        envelope.getMessages().clear();
        if (messages != null) {
            envelope.getMessages().addAll(messages);
        }
        envelope.syncRoundCountFromMessages();
        saveEnvelope(conversationId, envelope);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(keyOf(conversationId));
    }

    public MemoryEnvelope loadEnvelope(String conversationId) {
        String json = redisTemplate.opsForValue().get(keyOf(conversationId));
        if (json == null || json.isBlank()) {
            return MemoryEnvelope.empty();
        }
        try {
            if (json.stripLeading().startsWith("[")) {
                List<Message> legacy = parseMessageList(objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
                }));
                MemoryEnvelope envelope = new MemoryEnvelope("", 0, legacy);
                envelope.syncRoundCountFromMessages();
                return envelope;
            }
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {
            });
            String summary = String.valueOf(root.getOrDefault("summary", ""));
            int roundCount = root.get("roundCount") instanceof Number n ? n.intValue() : 0;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawMessages = (List<Map<String, Object>>) root.get("messages");
            List<Message> messages = rawMessages == null ? new ArrayList<>() : parseMessageList(rawMessages);
            MemoryEnvelope envelope = new MemoryEnvelope(summary, roundCount, messages);
            envelope.syncRoundCountFromMessages();
            return envelope;
        } catch (Exception e) {
            log.warn("读取会话记忆失败，返回空历史: conversationId={}, err={}", conversationId, e.getMessage());
            return MemoryEnvelope.empty();
        }
    }

    public void saveEnvelope(String conversationId, MemoryEnvelope envelope) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("v", SCHEMA_VERSION);
            root.put("summary", envelope.getSummary() == null ? "" : envelope.getSummary());
            root.put("roundCount", envelope.getRoundCount());
            List<Map<String, String>> items = new ArrayList<>(envelope.getMessages().size());
            for (Message message : envelope.getMessages()) {
                items.add(Map.of("type", message.getMessageType().name(), "content", message.getText()));
            }
            root.put("messages", items);
            redisTemplate.opsForValue().set(keyOf(conversationId), objectMapper.writeValueAsString(root), ttl);
        } catch (Exception e) {
            log.error("写入会话记忆失败: conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    // ==================== 会话成员绑定（防串号） ====================

    public void bindUser(String conversationId, Long userId) {
        redisTemplate.opsForValue().set(MEMBER_KEY_PREFIX + conversationId, String.valueOf(userId), ttl);
    }

    public boolean isOwner(String conversationId, Long userId) {
        String owner = redisTemplate.opsForValue().get(MEMBER_KEY_PREFIX + conversationId);
        return owner != null && owner.equals(String.valueOf(userId));
    }

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

    public static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private List<Message> parseMessageList(List<Map<String, Object>> items) {
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
    }

    private String keyOf(String conversationId) {
        return MEMORY_KEY_PREFIX + conversationId;
    }
}
