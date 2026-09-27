package com.shumamall.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单统计视图对象（Feign 接收 order 服务统计数据）。
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
