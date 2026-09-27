package com.shumamall.permission.service;

import java.util.Set;

/**
 * 权限缓存服务。
 * <p>
 * 使用 Redis 缓存用户权限编码集合，减少数据库查询。
 * key 格式：perm:user:{userId}，value 为权限编码 JSON 数组。
 */
public interface PermissionCacheService {

    /**
     * 获取缓存的用户权限编码集合。
     * <p>
     * 缓存未命中时自动从数据库加载并回填缓存。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    Set<String> getCachedPermissions(Long userId);

    /**
     * 清除用户权限缓存。
     * <p>
     * 角色/权限变更后调用，下次查询自动从数据库拉新。
     *
     * @param userId 用户 ID
     */
    void evictUserCache(Long userId);
}
