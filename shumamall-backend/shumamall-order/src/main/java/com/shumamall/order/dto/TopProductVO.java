package com.shumamall.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 热销商品排行项（按销售额聚合，供管理端仪表盘展示 Top 商品）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopProductVO {

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 销售件数 */
    private Integer salesQuantity;

    /** 销售额（小计之和） */
    private BigDecimal salesAmount;
}
