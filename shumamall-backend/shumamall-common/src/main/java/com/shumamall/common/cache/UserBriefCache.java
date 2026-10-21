package com.shumamall.common.cache;

/**
 * 用户展示资料缓存键。评论服务写入，用户改昵称时删除。
 */
public final class UserBriefCache {

    public static final String KEY_PREFIX = "user:brief:";

    /** 改昵称会主动删除；TTL 用来兜住没走更新接口的变更 */
    public static final long TTL_MINUTES = 30;

    private UserBriefCache() {
    }

    public static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
