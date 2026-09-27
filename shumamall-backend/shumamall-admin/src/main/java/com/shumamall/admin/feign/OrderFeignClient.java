package com.shumamall.admin.feign;

import com.shumamall.admin.dto.OrderStatisticsVO;
import com.shumamall.admin.dto.SalesTrendItemVO;
import com.shumamall.admin.dto.TopProductVO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单服务 Feign 客户端。
 * <p>
 * 对接 order 服务内部接口（无鉴权，供服务间调用）。
 *
 * @author ShuMaMall Team
 */
@FeignClient(name = "shumamall-order", path = "/api/v1/internal/orders")
public interface OrderFeignClient {

    /**
     * 查询订单统计（总订单数 / 今日订单数 / 今日销售额）。
     *
     * @return 订单统计
     */
    @GetMapping("/statistics")
    R<OrderStatisticsVO> getStatistics();

    /**
     * 查询近 N 天销售趋势。
     *
     * @param days 天数
     * @return 销售趋势列表
     */
    @GetMapping("/sales-trend")
    R<List<SalesTrendItemVO>> getSalesTrend(@RequestParam("days") int days);

    /**
     * 查询热销商品排行。
     *
     * @param limit 返回条数
     * @return 热销商品列表
     */
    @GetMapping("/top-products")
    R<List<TopProductVO>> getTopProducts(@RequestParam("limit") int limit);
}
