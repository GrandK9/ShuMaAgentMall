package com.shumamall.order.seckill.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动 DTO（管理端列表/详情）。
 */
@Data
public class SeckillActivityDTO {

    private Long id;

    private String name;

    private Long skuId;

    private Long productId;

    private BigDecimal seckillPrice;

    /** 活动库存总量（配置值） */
    private Integer totalStock;

    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 状态 0-未上线 1-已上线 2-已结束 */
    private Integer status;

    private String statusLabel;

    /**
     * Redis 中剩余可抢库存。
     * <p>
     * 未预热（活动未上线）或 Redis 不可用时为 null —— 前端据此显示「未开始」，
     * 而不是把 0 误读成「已抢光」。
     */
    private Integer remainingStock;
}
