package com.shumamall.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 商品读缓存服务。
 * <p>
 * 对用户端高频读（商品详情 / 默认商品列表）提供 Redis 缓存，并实现经典的
 * "缓存三防"：
 * <ul>
 *   <li>穿透：DB 无数据时缓存 {@link #EMPTY_VALUE} 空标记（60s），避免无效 key 反复直击 DB；</li>
 *   <li>击穿：热点 key 过期瞬间用 Redis 互斥锁（SET NX EX 3s）保证只有一个线程回源重建，
 *       其余线程短暂自旋后读取新缓存，兜底直查 DB；</li>
 *   <li>雪崩：缓存 TTL 统一叠加 0~5 分钟随机抖动，避免大量 key 同时过期。</li>
 * </ul>
 * 一致性：写路径（商品更新/上下架/删改库存/销量评论回写）统一调用 {@link #evict(Long)}
 * 删除详情 key 与默认列表 key，下次读取自动回源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 空值缓存标记（防穿透） */
    private static final String EMPTY_VALUE = "EMPTY";
    /** 空值缓存 TTL：60s */
    private static final Duration EMPTY_TTL = Duration.ofSeconds(60);
    /** 互斥锁 TTL：3s，锁持有者需在此期间完成 DB 查询 + 回填 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    /** 未抢到锁时的自旋重试次数 */
    private static final int RETRY_TIMES = 3;
    /** 自旋重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 100L;
    /** 雪崩防护：TTL 随机抖动上限（秒） */
    private static final long TTL_JITTER_SECONDS = 300L;

    /** 默认商品列表缓存 key */
    public static final String LIST_KEY = "product:list:default";

    /** 商品详情缓存 key */
    public static String detailKey(Long productId) {
        return "product:detail:" + productId;
    }

    /**
     * 带"缓存三防"的读取：命中缓存直接返回；未命中回源 DB 并回填。
     *
     * @param key      缓存 key
     * @param type     值类型（支持嵌套泛型，如 PageResult&lt;ProductVO&gt;）
     * @param ttl      缓存有效期（实际叠加随机抖动）
     * @param dbLoader DB 回源加载器；返回 null 表示"数据不存在"，会写入空值标记
     * @return 缓存或 DB 中的值；数据不存在返回 null
     */
    public <T> T getWithCache(String key, TypeReference<T> type, Duration ttl, Supplier<T> dbLoader) {
        // 1. 读缓存
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (EMPTY_VALUE.equals(cached)) {
                return null;
            }
            T value = parse(cached, type);
            if (value != null) {
                return value;
            }
            // 反序列化失败视为脏数据：删除后回源重建
            stringRedisTemplate.delete(key);
        }

        // 2. 互斥锁防击穿：只允许一个线程回源重建，其余等待读新缓存
        String lockKey = key + ":lock";
        boolean locked = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL));
        if (locked) {
            try {
                // 双检：等待锁期间可能已有其他线程回填
                String again = stringRedisTemplate.opsForValue().get(key);
                if (again != null) {
                    return EMPTY_VALUE.equals(again) ? null : parse(again, type);
                }
                T value = dbLoader.get();
                write(key, value, ttl);
                return value;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        // 3. 未抢到锁：短暂自旋，等重建线程写完后再读缓存
        for (int i = 0; i < RETRY_TIMES; i++) {
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String retry = stringRedisTemplate.opsForValue().get(key);
            if (retry != null) {
                return EMPTY_VALUE.equals(retry) ? null : parse(retry, type);
            }
        }

        // 4. 兜底：直查 DB（不缓存，避免阻塞重建线程）
        return dbLoader.get();
    }

    /**
     * 删除商品相关缓存（详情 + 默认列表），写路径调用以保证缓存一致性。
     *
     * @param productId 商品 ID
     */
    public void evict(Long productId) {
        if (productId == null) {
            return;
        }
        stringRedisTemplate.delete(List.of(detailKey(productId), LIST_KEY));
    }

    private void write(String key, Object value, Duration ttl) {
        if (value == null) {
            // 空值缓存：防穿透，TTL 固定较短
            stringRedisTemplate.opsForValue().set(key, EMPTY_VALUE, EMPTY_TTL);
            return;
        }
        // 雪崩防护：TTL 叠加随机抖动
        Duration jittered = ttl.plusSeconds(ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS));
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), jittered);
        } catch (JsonProcessingException e) {
            log.warn("商品缓存序列化失败, key={}", key, e);
        }
    }

    private <T> T parse(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("商品缓存反序列化失败, key 内容异常", e);
            return null;
        }
    }
}
