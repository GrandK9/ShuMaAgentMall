package com.shumamall.order.dto;

import com.shumamall.order.enums.OrderStatusEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单前端视图 VO（简化版）。
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private String statusLabel;

    /** 所有可选状态 */
    public OrderStatusEnum[] getStatusList() {
        return OrderStatusEnum.values();
    }
}
