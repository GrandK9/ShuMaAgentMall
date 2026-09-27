package com.shumamall.search.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置。
 * <p>
 * 声明商品增量同步队列并绑定到商品变更交换机：product 服务发布商品变更消息，
 * search 服务消费后增量同步 MySQL → ES。
 * 交换机为 fanout，agent 服务以独立队列绑定同一交换机，各自消费互不影响。
 * <p>
 * 队列额外挂载死信交换机：消费端重试仍失败的消息 nack(requeue=false) 后被路由到死信队列，
 * 既不会静默丢失，也不会在业务队列与消费者之间无限空转。
 */
@Configuration
public class RabbitConfig {

    @Value("${shumamall.search.sync-exchange:shumamall.product.sync.exchange}")
    private String syncExchange;

    @Value("${shumamall.search.sync-queue:search.sync.queue}")
    private String syncQueue;

    @Value("${shumamall.search.dlx:shumamall.product.sync.dlx}")
    private String deadLetterExchange;

    @Value("${shumamall.search.dlq:search.sync.queue.dlq}")
    private String deadLetterQueue;

    @Bean
    public FanoutExchange productSyncExchange() {
        return new FanoutExchange(syncExchange, true, false);
    }

    /**
     * 业务队列，携带死信参数。
     * <p>
     * 注意：给已存在的同名队列补死信参数会触发 PRECONDITION_FAILED
     * （broker 拒绝以不同参数重复声明同一队列）。首次升级需先删除旧的
     * {@code search.sync.queue}（无死信参数）再启动服务。
     */
    @Bean
    public Queue productSyncQueue() {
        return QueueBuilder.durable(syncQueue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .build();
    }

    /** 死信交换机（direct）：仅承接消费失败的消息。 */
    @Bean
    public DirectExchange productSyncDeadLetterExchange() {
        return new DirectExchange(deadLetterExchange, true, false);
    }

    /** 死信队列：失败消息在此堆积等待人工排查/重放，不会丢失。 */
    @Bean
    public Queue productSyncDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding productSyncDeadLetterBinding(Queue productSyncDeadLetterQueue,
                                                DirectExchange productSyncDeadLetterExchange) {
        return BindingBuilder.bind(productSyncDeadLetterQueue)
                .to(productSyncDeadLetterExchange)
                .with(deadLetterQueue);
    }

    @Bean
    public Binding productSyncBinding(Queue productSyncQueue, FanoutExchange productSyncExchange) {
        return BindingBuilder.bind(productSyncQueue).to(productSyncExchange);
    }

    /**
     * 消息体 JSON 反序列化。
     * <p>
     * 默认 SimpleMessageConverter 处理不了 product 发来的 JSON 消息体，会抛
     * MessageConversionException 并被容器直接丢弃。声明该 Bean 后监听容器自动采用 JSON 转换。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
