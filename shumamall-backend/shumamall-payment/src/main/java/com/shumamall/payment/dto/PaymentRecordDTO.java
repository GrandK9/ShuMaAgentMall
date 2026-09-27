package com.shumamall.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录 DTO（查询返回）。
 */
@Data
public class PaymentRecordDTO {

    /** 记录ID */
    private Long id;

    /** 支付流水号 */
    private String paymentNo;

    /** 订单编号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付方式 1-微信 2-支付宝 */
    private Integer paymentMethod;

    /** 状态 0-待支付 1-支付成功 2-支付失败 3-已退款 */
    private Integer status;

    /** 状态文字描述 */
    private String statusLabel;

    /** 第三方支付流水号 */
    private String thirdPartyNo;

    /** 支付成功时间 */
    private LocalDateTime paidAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
