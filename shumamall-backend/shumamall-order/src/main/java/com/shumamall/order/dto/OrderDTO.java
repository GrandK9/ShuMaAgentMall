package com.shumamall.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单完整 DTO。
 */
@Data
public class OrderDTO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private BigDecimal freightAmount;
    private Integer status;
    private String statusLabel;
    private Integer paymentMethod;
    private String paymentNo;
    private LocalDateTime paymentTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime receiveTime;
    private String remark;
    private String addressSnapshot;
    private String cancelReason;
    private LocalDateTime createdAt;

    /** 订单明细列表 */
    private List<OrderItemDTO> items;
}
