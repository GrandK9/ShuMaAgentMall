package com.shumamall.order.seckill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 秒杀抢购请求。
 * <p>
 * 不带数量字段：当前实现每人限购 1 件（Lua 用 {@code SETNX} 做资格占位，
 * 一个 key 只能表达「参与过一次」，无法表达「参与过 N 件」）。
 */
@Data
public class SeckillRequestDTO {

    /** 活动 ID */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    /** 收货地址 ID（异步建单时需要，故在抢购时就带上） */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 订单备注 */
    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
