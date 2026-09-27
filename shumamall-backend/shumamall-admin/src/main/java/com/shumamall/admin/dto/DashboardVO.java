package com.shumamall.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台仪表盘视图对象。
 * <p>
 * 聚合各微服务统计数据，供前端首页展示。
 *
 * @author ShuMaMall Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    /** 总订单数 */
    private Long totalOrders;

    /** 总商品数 */
    private Long totalProducts;

    /** 总用户数 */
    private Long totalUsers;

    /** 今日订单数 */
    private Long todayOrders;

    /** 今日销售额 */
    private BigDecimal todaySales;

    /** 近 7 天销售趋势（日期升序） */
    private List<SalesTrendItemVO> salesTrend;

    /** 热销商品 Top 10 */
    private List<TopProductVO> topProducts;
}
