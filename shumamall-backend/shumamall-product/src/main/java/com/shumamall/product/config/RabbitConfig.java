package com.shumamall.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置。
 * <p>
 * 声明商品变更事件交换机：商品创建/更新/删除后发布变更消息到该 fanout 交换机，
 * search（ES 索引）与 agent（向量索引）各自绑定独立队列消费，
 * 避免多服务共享同一队列导致的竞争消费（消息只被其中一个服务拿到）。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    @Value("${shumamall.product.sync-exchange:shumamall.product.sync.exchange}")
    private String syncExchange;

    @Bean
    public FanoutExchange productSyncExchange() {
        return new FanoutExchange(syncExchange, true, false);
    }

    /**
     * 消息体 JSON 序列化。
     * <p>
     * 默认 SimpleMessageConverter 只能处理 String/byte[]/Serializable，直接发送 POJO 会抛
     * MessageConversionException，而发布方法内 catch 了异常，会导致消息静默丢失。
     * 声明该 Bean 后 RabbitTemplate 自动改用 JSON 序列化，消费端（search/agent）以 JSON 还原，
     * 不依赖两侧类全限定名一致。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 生产者可靠投递回调。
     * <p>
     * {@link RabbitTemplateCustomizer} 会被 Spring Boot 的 RabbitAutoConfiguration 应用到
     * 自动配置好的 RabbitTemplate 上，因此无需自己声明 RabbitTemplate Bean：
     * <ul>
     *   <li><b>confirm 回调</b>：消息是否真正到达交换机（ack / nack）</li>
     *   <li><b>returns 回调</b>：消息到了交换机但没有任何队列可路由
     *       （需配合 {@code spring.rabbitmq.template.mandatory=true} 才会触发）</li>
     * </ul>
     * 两者均为异步回调，这里只记录日志用于排查。这也划清了"可靠投递"的边界：
     * confirm/returns 保证的是"消息进了 broker"，索引与数据库的最终一致仍由全量重建兜底。
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                String messageId = correlationData == null ? "<unknown>" : correlationData.getId();
                if (ack) {
                    log.debug("消息已到达交换机: messageId={}", messageId);
                } else {
                    log.error("消息未到达交换机（confirm=nack）: messageId={}, cause={}", messageId, cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                    "消息无队列可路由（已到达交换机）: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText()));
        };
    }
}
