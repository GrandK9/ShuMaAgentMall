package com.shumamall.order.seckill.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀建单消息的 MQ 拓扑。
 * <p>
 * 用 direct 交换机而不是 fanout：这条消息只有 order 自己消费（秒杀落在 order 服务内，
 * 消费者可直接调本地建单方法），没必要广播。routing key 直接用队列名，便于在控制台对照。
 * <p>
 * 业务队列挂了死信参数（{@code x-dead-letter-*}）：消费重试耗尽或建单失败的消息进死信队列
 * 保留原始报文，供人工核对与重放 —— 直接丢弃的话，"用户说抢到了但没订单"将无从追溯。
 */
@Configuration
public class SeckillRabbitConfig {

    @Value("${shumamall.seckill.mq.exchange}")
    private String exchange;

    @Value("${shumamall.seckill.mq.queue}")
    private String queue;

    @Value("${shumamall.seckill.mq.routing-key}")
    private String routingKey;

    @Value("${shumamall.seckill.mq.dlx}")
    private String deadLetterExchange;

    @Value("${shumamall.seckill.mq.dlq}")
    private String deadLetterQueue;

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(exchange, true, false);
    }

    /**
     * 秒杀建单队列。
     * <p>
     * 注意：给已存在的同名队列补死信参数会触发 PRECONDITION_FAILED，改动这里需要先删队列。
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .build();
    }

    @Bean
    public DirectExchange seckillDeadLetterExchange() {
        return new DirectExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue seckillDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillOrderQueue).to(seckillExchange).with(routingKey);
    }

    @Bean
    public Binding seckillDeadLetterBinding(Queue seckillDeadLetterQueue, DirectExchange seckillDeadLetterExchange) {
        return BindingBuilder.bind(seckillDeadLetterQueue).to(seckillDeadLetterExchange).with(deadLetterQueue);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
