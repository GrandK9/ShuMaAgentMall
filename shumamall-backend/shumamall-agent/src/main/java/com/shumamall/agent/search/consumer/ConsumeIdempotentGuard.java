package com.shumamall.agent.search.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消费幂等守卫。
 * <p>
 * 基于 Redis {@code SET key value NX EX ttl} 的原子性实现「同一消息只处理一次」：
 * <ol>
 *   <li>{@link #tryAcquire(String)} 抢占处理权；返回 false 说明该消息已被处理过（重复投递）</li>
 *   <li>业务处理成功：保留占位键，TTL 内的重复消息一律跳过</li>
 *   <li>业务处理失败：{@link #release(String)} 释放占位键，使重试或人工重放能再次处理</li>
 * </ol>
 * 用 Redis 而非本地缓存：消费端可能多实例部署，幂等状态必须跨实例共享。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumeIdempotentGuard {

    private static final String KEY_PREFIX = "mq:idempotent:";

    /** 幂等窗口：同一消息在此时间内重复投递都会被跳过 */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * 抢占消息处理权。
     *
     * @param messageId 消息唯一标识
     * @return true 表示可以处理；false 表示该消息已被处理过
     */
    public boolean tryAcquire(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            // 无消息 ID 无法去重，放行处理（降级但不阻断）
            log.warn("消息缺少 messageId，跳过幂等校验");
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + messageId, "1", TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // Redis 不可用时放行（fail-open）：本链路是对索引的覆盖写，重复处理不会产生错误数据；
            // 而因守卫自身故障让所有消息涌入死信队列，代价更大。
            log.warn("幂等校验不可用，放行处理: messageId={}, err={}", messageId, e.getMessage());
            return true;
        }
    }

    /**
     * 释放处理权（仅在处理失败时调用，使消息可被重新处理）。
     *
     * @param messageId 消息唯一标识
     */
    public void release(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + messageId);
        } catch (Exception e) {
            log.warn("释放幂等占位键失败: messageId={}, err={}", messageId, e.getMessage());
        }
    }
}
