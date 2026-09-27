package com.shumamall.permission.service.impl;

import com.shumamall.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限缓存服务单元测试（仅编译验证，不依赖 Spring 上下文）。
 * <p>
 * 覆盖：缓存命中直接返回、未命中回源 DB 并回填、变更时清除缓存。
 */
class PermissionCacheServiceImplTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final PermissionCacheServiceImpl service = new PermissionCacheServiceImpl(redisTemplate, permissionService);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void hitCache_returnsCachedPermissionsWithoutDb() {
        when(valueOps.get("perm:user:1")).thenReturn("product:edit,order:view");

        Set<String> permissions = service.getCachedPermissions(1L);

        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("product:edit"));
        // 命中缓存时不得回源 DB
        verify(permissionService, never()).getUserPermissionCodes(any());
    }

    @Test
    void missCache_loadsFromDbAndBackfills() {
        when(valueOps.get("perm:user:1")).thenReturn(null);
        when(permissionService.getUserPermissionCodes(1L)).thenReturn(Set.of("product:edit"));

        Set<String> permissions = service.getCachedPermissions(1L);

        assertTrue(permissions.contains("product:edit"));
        verify(valueOps).set(eq("perm:user:1"), eq("product:edit"), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void missCache_withEmptyPermissions_alsoBackfills() {
        when(valueOps.get("perm:user:1")).thenReturn(null);
        when(permissionService.getUserPermissionCodes(1L)).thenReturn(Set.of());

        Set<String> permissions = service.getCachedPermissions(1L);

        assertTrue(permissions.isEmpty());
        verify(valueOps).set(eq("perm:user:1"), eq(""), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void evictUserCache_deletesKey() {
        service.evictUserCache(1L);

        verify(redisTemplate).delete("perm:user:1");
    }
}
