package com.shumamall.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付请求 DTO。
 */
@Data
public class PayRequestDTO {

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /** 支付方式 1-微信 2-支付宝 */
    @NotNull(message = "支付方式不能为空")
    private Integer paymentMethod;

    /** 支付金额 */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额必须大于0")
    private BigDecimal amount;
}
