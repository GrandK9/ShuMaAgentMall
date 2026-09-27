package com.shumamall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求 DTO。
 */
@Data
public class OrderCreateDTO {

    /** 商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 商品名称 */
    @NotNull(message = "商品名称不能为空")
    private String productName;

    /** SKU ID（直接购买时必传） */
    private Long skuId;

    /** 购买数量 */
    @Min(1)
    private Integer quantity = 1;

    /** 收货地址ID */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 订单备注 */
    private String remark;

    /** 购物车项ID列表（从购物车结算时传入） */
    private List<Long> cartItemIds;
}
