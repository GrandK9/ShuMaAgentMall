package com.shumamall.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细 DTO。
 */
@Data
public class OrderItemDTO {

    private Long id;
    private Long skuId;
    private Long productId;
    private String productName;
    private String skuSpecs;
    private String productImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}
