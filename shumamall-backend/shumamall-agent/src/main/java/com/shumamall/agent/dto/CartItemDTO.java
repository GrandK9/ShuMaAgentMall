package com.shumamall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目 DTO（Feign 调用 order 服务后精简字段）。
 */
@Data
public class CartItemDTO {

    private Long id;
    private Long skuId;
    private Long productId;
    private String productName;
    private String productImage;
    private String skuSpecs;
    private BigDecimal price;
    private Integer quantity;
    private Integer selected;

    /** 小计金额（price × quantity） */
    private BigDecimal subtotal;
}
