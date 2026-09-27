package com.shumamall.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付响应 DTO。
 */
@Data
public class PayResponseDTO {

    /** 记录ID */
    private Long paymentId;

    /** 支付流水号 */
    private String paymentNo;

    /** 订单编号 */
    private String orderNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 状态 0-待支付 1-支付成功 2-支付失败 3-已退款 */
    private Integer status;

    /** 状态文字描述 */
    private String statusLabel;

    /** 支付成功时间 */
    private LocalDateTime paidAt;
}
