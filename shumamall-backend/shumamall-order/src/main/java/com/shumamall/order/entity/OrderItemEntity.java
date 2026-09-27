package com.shumamall.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体。
 */
@Data
@TableName("order_item")
public class OrderItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** SKU ID */
    private Long skuId;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** SKU规格值 JSON */
    private String skuSpecs;

    /** 商品图片 */
    private String productImage;

    /** 单价 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计 */
    private BigDecimal subtotal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
