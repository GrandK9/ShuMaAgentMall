package com.shumamall.product.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商品变更事件发布器。
 * <p>
 * 商品创建/更新/删除后发布消息到商品变更交换机（fanout），
 * 由 search（ES 索引）与 agent（向量索引）各自消费，增量更新各自的索引。
 * 发布失败不影响主流程（索引可随时全量重建兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_DELETE = "DELETE";

    private final RabbitTemplate rabbitTemplate;

    @Value("${shumamall.product.sync-exchange:shumamall.product.sync.exchange}")
    private String syncExchange;

    /**
     * 发布商品新增/更新事件。
     *
     * @param productId 商品 ID
     */
    public void publishUpsert(Long productId) {
        publish(productId, ACTION_UPSERT);
    }

    /**
     * 发布商品删除事件。
     *
     * @param productId 商品 ID
     */
    public void publishDelete(Long productId) {
        publish(productId, ACTION_DELETE);
    }

    private void publish(Long productId, String action) {
        ProductSyncMessage message = new ProductSyncMessage();
        message.setProductId(productId);
        message.setAction(action);
        message.setTimestamp(System.currentTimeMillis());
        // 消息唯一标识：既用于生产者 confirm 回调按消息定位，也是消费端幂等去重的键
        message.setMessageId("product-sync-" + productId + "-" + action + "-" + message.getTimestamp());
        try {
            // fanout 交换机忽略 routing key，各订阅队列均收到同一份消息。
            // 携带 CorrelationData 后，confirm 回调才能把 broker 的确认结果与具体消息对应起来。
            rabbitTemplate.convertAndSend(syncExchange, "", message,
                    new CorrelationData(message.getMessageId()));
            log.debug("商品事件发布: messageId={}, productId={}, action={}",
                    message.getMessageId(), productId, action);
        } catch (Exception e) {
            // 发布失败不影响主流程：索引与数据库不一致时可通过全量重建兜底。
            // 注意这里是同步异常（连接不可用等）；异步的投递结果由 confirm / returns 回调记录。
            log.error("商品事件发布失败: messageId={}, productId={}, action={}",
                    message.getMessageId(), productId, action, e);
        }
    }
}
