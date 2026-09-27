package com.shumamall.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车 DTO。
 */
@Data
public class CartDTO {

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
