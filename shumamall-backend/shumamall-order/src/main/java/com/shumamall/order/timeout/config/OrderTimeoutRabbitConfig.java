package com.shumamall.order.timeout.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单支付超时 MQ 拓扑：延迟队列（队列级 TTL）+ 死信转发至超时处理队列。
 * <p>
 * 建单后消息进入延迟队列，停留 {@code pay-timeout-minutes} 后过期并经 DLX 进入处理队列消费。
 * 支付成功或用户取消时不撤回已投递消息，消费端由 {@code timeoutCancel} 的状态 CAS 保证幂等。
 */
@Configuration
public class OrderTimeoutRabbitConfig {

    @Value("${shumamall.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    /** 大于 0 时优先于 {@link #payTimeoutMinutes}，用于联调缩短延迟（毫秒） */
    @Value("${shumamall.order.pay-timeout-ms:0}")
    private long payTimeoutMs;

    @Value("${shumamall.order.timeout.mq.delay-exchange}")
    private String delayExchange;

    @Value("${shumamall.order.timeout.mq.delay-queue}")
    private String delayQueue;

    @Value("${shumamall.order.timeout.mq.delay-routing-key}")
    private String delayRoutingKey;

    @Value("${shumamall.order.timeout.mq.exchange}")
    private String processExchange;

    @Value("${shumamall.order.timeout.mq.queue}")
    private String processQueue;

    @Value("${shumamall.order.timeout.mq.routing-key}")
    private String processRoutingKey;

    @Value("${shumamall.order.timeout.mq.dlx}")
    private String deadLetterExchange;

    @Value("${shumamall.order.timeout.mq.dlq}")
    private String deadLetterQueue;

    @Bean
    public DirectExchange orderTimeoutDelayExchange() {
        return new DirectExchange(delayExchange, true, false);
    }

    /**
     * 延迟队列：无消费者，消息在队列中停留 {@code pay-timeout-minutes} 后过期并进入死信处理队列。
     * <p>
     * 修改 TTL 需先删除同名队列再重建，否则 RabbitMQ 会返回 PRECONDITION_FAILED。
     */
    @Bean
    public Queue orderTimeoutDelayQueue() {
        int ttlMs = payTimeoutMs > 0 ? (int) payTimeoutMs : payTimeoutMinutes * 60_000;
        return QueueBuilder.durable(delayQueue)
                .withArgument("x-message-ttl", ttlMs)
                .withArgument("x-dead-letter-exchange", processExchange)
                .withArgument("x-dead-letter-routing-key", processRoutingKey)
                .build();
    }

    @Bean
    public Binding orderTimeoutDelayBinding(Queue orderTimeoutDelayQueue, DirectExchange orderTimeoutDelayExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderTimeoutDelayExchange).with(delayRoutingKey);
    }

    @Bean
    public DirectExchange orderTimeoutProcessExchange() {
        return new DirectExchange(processExchange, true, false);
    }

    @Bean
    public Queue orderTimeoutProcessQueue() {
        return QueueBuilder.durable(processQueue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .build();
    }

    @Bean
    public DirectExchange orderTimeoutDeadLetterExchange() {
        return new DirectExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue orderTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding orderTimeoutProcessBinding(Queue orderTimeoutProcessQueue,
                                              DirectExchange orderTimeoutProcessExchange) {
        return BindingBuilder.bind(orderTimeoutProcessQueue).to(orderTimeoutProcessExchange).with(processRoutingKey);
    }

    @Bean
    public Binding orderTimeoutDeadLetterBinding(Queue orderTimeoutDeadLetterQueue,
                                                 DirectExchange orderTimeoutDeadLetterExchange) {
        return BindingBuilder.bind(orderTimeoutDeadLetterQueue).to(orderTimeoutDeadLetterExchange).with(deadLetterQueue);
    }
}
