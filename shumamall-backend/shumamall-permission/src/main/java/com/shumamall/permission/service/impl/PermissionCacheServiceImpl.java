package com.shumamall.permission.service.impl;

import com.shumamall.permission.service.PermissionCacheService;
import com.shumamall.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 权限缓存服务实现。
 * <p>
 * 使用 Redis hash 结构缓存用户权限编码集合。
 * key 格式：perm:user:{userId}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheServiceImpl implements PermissionCacheService {

    private static final String CACHE_KEY_PREFIX = "perm:user:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final PermissionService permissionService;

    @Override
    public Set<String> getCachedPermissions(Long userId) {
        String key = CACHE_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // 缓存命中，从 JSON 数组转换为 Set
            String[] parts = cached.split(",");
            if (parts.length == 1 && parts[0].isEmpty()) {
                return Collections.emptySet();
            }
            return Set.of(parts);
        }

        // 缓存未命中，从数据库加载
        Set<String> permissions = permissionService.getUserPermissionCodes(userId);
        if (permissions == null) {
            permissions = Collections.emptySet();
        }

        // 回填缓存
        String value = String.join(",", permissions);
        redisTemplate.opsForValue().set(key, value, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("权限缓存已回填: userId={}, count={}", userId, permissions.size());

        return permissions;
    }

    @Override
    public void evictUserCache(Long userId) {
        String key = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("权限缓存已清除: userId={}", userId);
    }
}
