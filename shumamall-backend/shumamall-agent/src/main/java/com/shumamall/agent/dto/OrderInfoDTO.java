package com.shumamall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单摘要 DTO（Feign 调用 order 服务后精简字段）。
 */
@Data
public class OrderInfoDTO {

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private String statusLabel;
    private String addressSnapshot;
    private LocalDateTime createdAt;
}
