package com.shumamall.order.seckill.constant;

/**
 * 秒杀常量。
 * <p>
 * 集中定义 Redis key 前缀、Lua 脚本返回码、活动与资格记录状态码，
 * 避免这些"魔法值"散落在 Service / MQ / 对账任务各处后各自漂移。
 */
public final class SeckillConstants {

    private SeckillConstants() {
    }

    // ==================== Redis key ====================

    /** 剩余库存：seckill:stock:{activityId}，值为十进制数字字符串 */
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";

    /** 一人一单占位：seckill:user:{activityId}:{userId} */
    public static final String USER_KEY_PREFIX = "seckill:user:";

    /** 活动快照：seckill:activity:{activityId}，值为 JSON，避免每次抢购都查库 */
    public static final String ACTIVITY_KEY_PREFIX = "seckill:activity:";

    /** 资格占位 TTL（秒）：活动结束后仍需保留，否则同一用户可在残留期内重复抢 */
    public static final long USER_KEY_TTL_SECONDS = 7L * 24 * 3600;

    /**
     * Redis 中活动数据（库存 key + 活动快照）的最小 TTL（秒）。
     * <p>
     * 实际 TTL 按「活动结束时间 + 1 小时」计算（见 SeckillServiceImpl#redisTtlSeconds），
     * 该常量只作下限：活动结束时间填得离当前太近时，避免 key 秒过期。
     */
    public static final long ACTIVITY_KEY_TTL_SECONDS = 12L * 3600;

    // ==================== Lua 脚本返回码 ====================

    /** 预扣成功 */
    public static final long LUA_OK = 1L;

    /** 库存未预热（key 不存在）：活动未预热或 Redis 数据被清 */
    public static final long LUA_NOT_PREHEATED = -1L;

    /** 库存不足 */
    public static final long LUA_STOCK_NOT_ENOUGH = -2L;

    /** 重复下单（该用户在本活动已有资格占位） */
    public static final long LUA_DUPLICATE = -3L;

    // ==================== 活动状态 ====================

    /** 未上线（不可抢） */
    public static final int ACTIVITY_OFFLINE = 0;

    /** 已上线（到时间窗内可抢） */
    public static final int ACTIVITY_ONLINE = 1;

    /** 已结束（活动时间窗已过，人工置位或由状态刷新任务写入） */
    public static final int ACTIVITY_FINISHED = 2;

    // ==================== 资格记录状态 ====================

    /** 已预扣，等待异步建单 */
    public static final int RECORD_QUEUED = 0;

    /** 已成功建单（order_no 已回填） */
    public static final int RECORD_CREATED = 1;

    /** 建单失败，且已回补 Redis 库存与资格占位 */
    public static final int RECORD_FAILED = 2;

    /** 关联订单已取消，Redis 库存已回补（资格占位保留，防止取消后反复重抢） */
    public static final int RECORD_CANCELLED = 3;

    /**
     * 建单中（消费方已抢到处理权，尚未回填结果）。
     * <p>
     * 这个中间态专门用来消除重复建单：消费方在建单前把记录 CAS 到本状态，
     * 消息重复投递时会因 CAS 失败而跳过。少了它，"建单成功但回填订单号前进程挂掉"
     * 这一窗口内的重投就会建出第二笔订单。
     */
    public static final int RECORD_PROCESSING = 4;
}
