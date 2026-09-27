package com.shumamall.common.auth;

/**
 * 安全上下文，通过 ThreadLocal 持有当前请求的用户 ID。
 * <p>
 * 配合 {@link TokenFilter} 使用，Filter 中解析 JWT 后设置 userId，
 * 在 Controller / Service 中通过 {@link #getUserId()} 获取。
 * 请求结束由 filter 清理，避免内存泄漏。
 */
public class SecurityContext {

    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        currentUserId.set(userId);
    }

    public static Long getUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }
}
