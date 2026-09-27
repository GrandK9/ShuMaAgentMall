package com.shumamall.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单统计视图对象（供管理端仪表盘聚合调用）。
 *
 * @author ShuMaMall Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsVO {

    /** 总订单数 */
    private Long totalOrders;

    /** 今日订单数 */
    private Long todayOrders;

    /** 今日销售额 */
    private BigDecimal todaySales;
}
