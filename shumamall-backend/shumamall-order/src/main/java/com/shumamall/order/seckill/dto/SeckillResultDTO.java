package com.shumamall.order.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀抢购结果。
 * <p>
 * 抢购接口是「秒回」语义：预扣成功即返回本对象，status 通常是 0（排队中），
 * 前端凭 {@code requestId} 轮询查建单结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultDTO {

    /** 抢购凭证（查询建单结果用） */
    private String requestId;

    /** 状态 0-待建单 1-已建单 2-建单失败 3-订单已取消 4-建单中 */
    private Integer status;

    private String statusLabel;

    /** 建单成功后的订单号 */
    private String orderNo;

    /** 面向用户的提示文案 */
    private String message;
}
