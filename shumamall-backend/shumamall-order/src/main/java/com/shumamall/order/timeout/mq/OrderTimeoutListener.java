package com.shumamall.order.timeout.mq;

import com.rabbitmq.client.Channel;
import com.shumamall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单支付超时消费者：延迟到期后调用 {@link OrderService#timeoutCancel(Long)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = "${shumamall.order.timeout.mq.queue}")
    public void onMessage(OrderTimeoutMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || message.getOrderId() == null) {
            log.warn("无法处理的订单超时消息，直接确认丢弃: {}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            orderService.timeoutCancel(message.getOrderId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("订单超时取消失败，转入死信队列: orderId={}, orderNo={}",
                    message.getOrderId(), message.getOrderNo(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
