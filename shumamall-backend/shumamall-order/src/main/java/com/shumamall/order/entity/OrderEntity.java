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
 * 订单实体。
 */
@Data
@TableName("`order`")
public class OrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 订单状态 0-待付款 1-待发货 2-待收货 3-已完成 4-已取消 5-售后中 */
    private Integer status;

    /** 支付方式 1-微信 2-支付宝 */
    private Integer paymentMethod;

    /** 支付流水号 */
    private String paymentNo;

    /** 支付时间 */
    private LocalDateTime paymentTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 订单备注 */
    private String remark;

    /** 地址快照（JSON） */
    private String addressSnapshot;

    /** 取消原因 */
    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
