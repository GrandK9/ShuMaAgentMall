package com.shumamall.agent.config;

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
 * 绑定商品变更交换机到 Agent 自己的同步队列：product 服务发布商品变更消息后，
 * Agent 消费并增量更新向量索引（BM25 + KNN 的索引底座）。
 * <p>
 * 队列与 search 服务相互独立，fanout 交换机保证两边都能收到同一份消息。
 * 队列额外挂载死信交换机：消费端重试仍失败的消息 nack(requeue=false) 后进入死信队列，
 * 既不静默丢失，也不会无限重投。
 */
@Configuration
public class RabbitConfig {

    @Value("${shumamall.agent.search.sync-exchange:shumamall.product.sync.exchange}")
    private String syncExchange;

    @Value("${shumamall.agent.search.sync-queue:agent.search.sync.queue}")
    private String syncQueue;

    @Value("${shumamall.agent.search.dlx:shumamall.product.sync.dlx}")
    private String deadLetterExchange;

    @Value("${shumamall.agent.search.dlq:agent.search.sync.queue.dlq}")
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
     * {@code agent.search.sync.queue}（无死信参数）再启动服务。
     */
    @Bean
    public Queue agentProductSyncQueue() {
        return QueueBuilder.durable(syncQueue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .build();
    }

    /** 死信交换机（direct）：仅承接消费失败的消息。 */
    @Bean
    public DirectExchange agentProductSyncDeadLetterExchange() {
        return new DirectExchange(deadLetterExchange, true, false);
    }

    /** 死信队列：失败消息在此堆积等待人工排查/重放，不会丢失。 */
    @Bean
    public Queue agentProductSyncDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding agentProductSyncDeadLetterBinding(Queue agentProductSyncDeadLetterQueue,
                                                      DirectExchange agentProductSyncDeadLetterExchange) {
        return BindingBuilder.bind(agentProductSyncDeadLetterQueue)
                .to(agentProductSyncDeadLetterExchange)
                .with(deadLetterQueue);
    }

    @Bean
    public Binding agentProductSyncBinding(Queue agentProductSyncQueue, FanoutExchange productSyncExchange) {
        return BindingBuilder.bind(agentProductSyncQueue).to(productSyncExchange);
    }

    /**
     * 消息体 JSON 反序列化。
     * <p>
     * 默认 SimpleMessageConverter 无法把 product 发来的 JSON 还原成消息对象，
     * 会抛 MessageConversionException 并被容器直接丢弃（消息既不落库也不重投）。
     * 声明该 Bean 后监听容器自动采用 JSON 转换；两侧 {@code ProductSyncMessage}
     * 虽在不同包，但转换器按监听方法参数类型还原，不依赖消息头里的类名。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
