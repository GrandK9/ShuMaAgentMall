package com.shumamall.admin.service.impl;

import com.shumamall.admin.dto.DashboardVO;
import com.shumamall.admin.dto.OrderStatisticsVO;
import com.shumamall.admin.dto.SalesTrendItemVO;
import com.shumamall.admin.dto.TopProductVO;
import com.shumamall.admin.feign.OrderFeignClient;
import com.shumamall.admin.feign.ProductFeignClient;
import com.shumamall.admin.feign.UserFeignClient;
import com.shumamall.admin.service.DashboardService;
import com.shumamall.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 仪表盘服务实现。
 * <p>
 * 聚合用户、商品、订单三个服务的统计数据，单个服务不可用时降级返回 0。
 *
 * @author ShuMaMall Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;
    private final OrderFeignClient orderFeignClient;

    /**
     * 获取仪表盘数据，单个服务调用失败时降级返回 0 并记录警告日志。
     *
     * @return 仪表盘视图对象
     */
    @Override
    public DashboardVO getDashboard() {
        Long totalUsers = safeCallLong("shumamall-user", () -> {
            R<Long> resp = userFeignClient.countUsers();
            return resp != null ? resp.getData() : null;
        });
        Long totalProducts = safeCallLong("shumamall-product", () -> {
            R<Long> resp = productFeignClient.countProducts();
            return resp != null ? resp.getData() : null;
        });

        OrderStatisticsVO stats = safeCallStats("shumamall-order", orderFeignClient::getStatistics);
        Long totalOrders = stats != null ? stats.getTotalOrders() : null;
        Long todayOrders = stats != null ? stats.getTodayOrders() : null;
        BigDecimal todaySales = stats != null ? stats.getTodaySales() : null;

        List<SalesTrendItemVO> salesTrend = safeCallList("shumamall-order",
                () -> orderFeignClient.getSalesTrend(7));
        List<TopProductVO> topProducts = safeCallList("shumamall-order",
                () -> orderFeignClient.getTopProducts(10));

        return new DashboardVO(
                totalOrders != null ? totalOrders : 0L,
                totalProducts != null ? totalProducts : 0L,
                totalUsers != null ? totalUsers : 0L,
                todayOrders != null ? todayOrders : 0L,
                todaySales != null ? todaySales : BigDecimal.ZERO,
                salesTrend,
                topProducts
        );
    }

    /**
     * 安全调用返回 List 的 Feign 接口，捕获异常并返回空列表。
     */
    private <T> List<T> safeCallList(String serviceName, Supplier<R<List<T>>> supplier) {
        try {
            R<List<T>> resp = supplier.get();
            return resp != null ? resp.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Feign call to {} failed, falling back to empty list: {}", serviceName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 安全调用 Feign 接口（返回 Long），捕获异常并返回降级值。
     *
     * @param serviceName 服务名称（仅用于日志）
     * @param supplier    Feign 调用
     * @return 调用结果，失败时返回 null
     */
    private Long safeCallLong(String serviceName, Supplier<Long> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Feign call to {} failed, falling back to 0: {}", serviceName, e.getMessage());
            return null;
        }
    }

    /**
     * 安全调用订单统计 Feign 接口，捕获异常并返回降级值。
     *
     * @param serviceName 服务名称（仅用于日志）
     * @param supplier    Feign 调用
     * @return 调用结果，失败时返回 null
     */
    private OrderStatisticsVO safeCallStats(String serviceName, Supplier<R<OrderStatisticsVO>> supplier) {
        try {
            R<OrderStatisticsVO> resp = supplier.get();
            return resp != null ? resp.getData() : null;
        } catch (Exception e) {
            log.warn("Feign call to {} failed, falling back to null: {}", serviceName, e.getMessage());
            return null;
        }
    }
}
