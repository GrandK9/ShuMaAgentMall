package com.shumamall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加购物车请求 DTO。
 */
@Data
public class CartAddDTO {

    /** SKU ID */
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 数量 */
    @Min(value = 1, message = "数量不能小于1")
    private Integer quantity = 1;
}
