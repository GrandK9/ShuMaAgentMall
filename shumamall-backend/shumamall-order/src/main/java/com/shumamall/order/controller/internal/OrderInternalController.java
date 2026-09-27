package com.shumamall.order.controller.internal;

import com.shumamall.common.result.R;
import com.shumamall.order.dto.OrderStatisticsVO;
import com.shumamall.order.dto.SalesTrendItemVO;
import com.shumamall.order.dto.TopProductVO;
import com.shumamall.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单内部控制器。
 * <p>
 * 供微服务间调用（Feign），不对外暴露，不经过 TokenFilter 认证。
 * 目前用于支付服务回调更新订单状态。
 */
@Slf4j
@Tag(name = "内部接口-订单", description = "微服务间 Feign 调用的订单支付/退款回调、应付金额查询及仪表盘统计接口")
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderService orderService;

    /**
     * 支付成功回调——更新订单状态为已支付（待发货）。
     *
     * @param orderNo       订单编号
     * @param paymentMethod 支付方式 1-微信 2-支付宝
     * @param paymentNo     支付流水号
     * @return 操作结果
     */
    @PutMapping("/{orderNo}/pay")
    public R<Void> updateOrderPaid(@PathVariable String orderNo,
                                   @RequestParam Integer paymentMethod,
                                   @RequestParam String paymentNo) {
        orderService.updateOrderPaid(orderNo, paymentMethod, paymentNo);
        return R.ok();
    }

    /**
     * 退款回调——更新订单状态为已取消，并回补库存、负向回退销量。
     *
     * @param orderNo 订单编号
     * @return 操作结果
     */
    @PutMapping("/{orderNo}/refund")
    public R<Void> updateOrderRefunded(@PathVariable String orderNo) {
        orderService.updateOrderRefunded(orderNo);
        return R.ok();
    }

    /**
     * 查询订单应付金额（供支付服务在扣款前做金额强校验，避免客户端篡改支付金额）。
     *
     * @param orderNo 订单编号
     * @param userId  支付人用户ID（校验订单归属，禁止替他人订单付款）
     * @return 订单应付金额
     */
    @GetMapping("/{orderNo}/payable-amount")
    public R<BigDecimal> getPayableAmount(@PathVariable String orderNo,
                                          @RequestParam Long userId) {
        return R.ok(orderService.getPayableAmount(orderNo, userId));
    }

    /**
     * 订单统计（供管理端仪表盘 Feign 调用）。
     *
     * @return 订单统计
     */
    @GetMapping("/statistics")
    public R<OrderStatisticsVO> getStatistics() {
        return R.ok(orderService.getStatistics());
    }

    /**
     * 近 N 天销售趋势（供管理端仪表盘 Feign 调用）。
     *
     * @param days 天数（默认 7）
     * @return 销售趋势列表（日期升序）
     */
    @GetMapping("/sales-trend")
    public R<List<SalesTrendItemVO>> getSalesTrend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(orderService.getSalesTrend(days));
    }

    /**
     * 热销商品排行（供管理端仪表盘 Feign 调用）。
     *
     * @param limit 返回条数（默认 10）
     * @return 热销商品列表
     */
    @GetMapping("/top-products")
    public R<List<TopProductVO>> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(orderService.getTopProducts(limit));
    }
}
