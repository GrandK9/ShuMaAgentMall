package com.shumamall.payment.api;

import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 订单服务 Feign 客户端。
 * <p>
 * 用于支付成功后回调订单服务更新订单状态。
 * <p>
 * 刻意<b>不配</b> fallbackFactory，尽管公共配置已开启
 * {@code spring.cloud.openfeign.circuitbreaker.enabled}（见 shumamall-common.yaml）。
 * 原因：updateOrderPaid / updateOrderRefunded 的返回值在 PaymentServiceImpl 里是被忽略的
 * （只 catch 异常）。若加了 fallbackFactory，订单服务不可用时熔断会把异常吞成 R.failed 返回值，
 * 调用方既不抛异常也不看返回值 —— 结果「钱已收下、订单仍停在待支付」。
 * 现在由 catch 转成 SERVICE_UNAVAILABLE 抛出，整个支付接口失败，用户可重试（支付幂等）。
 */
@FeignClient("shumamall-order")
public interface OrderFeignClient {

    /**
     * 通知订单服务订单已支付。
     *
     * @param orderNo       订单编号
     * @param paymentMethod 支付方式
     * @param paymentNo     支付流水号
     * @return 操作结果
     */
    @PutMapping("/api/v1/internal/orders/{orderNo}/pay")
    R<Void> updateOrderPaid(@PathVariable("orderNo") String orderNo,
                            @RequestParam("paymentMethod") Integer paymentMethod,
                            @RequestParam("paymentNo") String paymentNo);

    /**
     * 通知订单服务订单已退款。
     *
     * @param orderNo 订单编号
     * @return 操作结果
     */
    @PutMapping("/api/v1/internal/orders/{orderNo}/refund")
    R<Void> updateOrderRefunded(@PathVariable("orderNo") String orderNo);

    /**
     * 查询订单应付金额（支付前金额强校验，金额以订单服务为准）。
     * <p>
     * 必须带 userId：订单服务据此校验订单归属，否则 A 用户可以替 B 用户的订单付款
     * （付完款订单仍归属 B，支付记录却记在 A 名下，形成脏数据）。
     *
     * @param orderNo 订单编号
     * @param userId  支付人用户ID
     * @return 订单应付金额
     */
    @GetMapping("/api/v1/internal/orders/{orderNo}/payable-amount")
    R<BigDecimal> getPayableAmount(@PathVariable("orderNo") String orderNo,
                                   @RequestParam("userId") Long userId);
}
