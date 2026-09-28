package com.shumamall.order.timeout.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单支付超时延迟消息生产者：建单成功后投递，到期后由消费者触发取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${shumamall.order.timeout.mq.delay-exchange}")
    private String delayExchange;

    @Value("${shumamall.order.timeout.mq.delay-routing-key}")
    private String delayRoutingKey;

    /**
     * 调度支付超时检查。
     * <p>
     * 发送失败只记日志、不向上抛出：订单已落库，由 {@code scheduledTimeoutCancel} 定时补偿取消。
     *
     * @param orderId 订单 ID
     * @param orderNo 订单号（日志用）
     */
    public void scheduleTimeoutCheck(Long orderId, String orderNo) {
        if (orderId == null) {
            return;
        }
        OrderTimeoutMessage message = new OrderTimeoutMessage(orderId, orderNo);
        try {
            rabbitTemplate.convertAndSend(delayExchange, delayRoutingKey, message);
            log.info("订单支付超时检查已投递: orderId={}, orderNo={}", orderId, orderNo);
        } catch (Exception e) {
            log.error("订单支付超时消息投递失败，等待定时任务补偿: orderId={}, orderNo={}", orderId, orderNo, e);
        }
    }
}
