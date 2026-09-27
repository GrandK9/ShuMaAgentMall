package com.shumamall.agent.search.consumer;

import com.rabbitmq.client.Channel;
import com.shumamall.agent.dto.ProductSyncMessage;
import com.shumamall.agent.search.service.ProductIndexWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 商品变更同步消费者（手动 ack）。
 * <p>
 * 监听 product 服务发布的商品变更消息，增量更新 Agent 的向量索引：
 * <ul>
 *   <li>UPSERT：拉取商品详情重新向量化并写入索引（已下架则从索引移除）</li>
 *   <li>DELETE：从索引删除文档</li>
 * </ul>
 * 可靠性由三件事构成：
 * <ol>
 *   <li><b>手动 ack</b>：处理成功才确认；失败不确认，避免「消息丢了却以为处理了」</li>
 *   <li><b>本地重试</b>：同一条消息最多处理 {@value #MAX_ATTEMPTS} 次；
 *       重试在消费线程内完成，不把消息重投回 broker，避免队列与消费者之间反复空转</li>
 *   <li><b>死信队列</b>：重试耗尽后 {@code basicNack(requeue=false)}，
 *       由队列的死信参数路由进 {@code agent.search.sync.queue.dlq}，既不丢也不无限重投</li>
 * </ol>
 * 处理前先经 {@link ConsumeIdempotentGuard} 做消息级幂等，重复投递只处理一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncListener {

    /** 操作：新增/更新 */
    public static final String ACTION_UPSERT = "UPSERT";
    /** 操作：删除 */
    public static final String ACTION_DELETE = "DELETE";

    /** 单条消息最大处理次数（首次 + 重试） */
    private static final int MAX_ATTEMPTS = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_BACKOFF_MS = 300L;

    private final ProductIndexWriter productIndexWriter;
    private final ConsumeIdempotentGuard idempotentGuard;

    @RabbitListener(queues = "${shumamall.agent.search.sync-queue:agent.search.sync.queue}")
    public void onMessage(ProductSyncMessage message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || message.getProductId() == null) {
            // 脏消息：直接确认丢弃，否则会永远卡在队列里被反复投递
            log.warn("收到空的商品同步消息，直接确认丢弃");
            channel.basicAck(deliveryTag, false);
            return;
        }

        String messageId = message.getMessageId();
        if (!idempotentGuard.tryAcquire(messageId)) {
            log.info("重复消息，跳过处理（消费幂等）: messageId={}, productId={}",
                    messageId, message.getProductId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            handleWithRetry(message, messageId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 先释放幂等键：否则人工从死信队列重放时会被当成重复消息跳过
            idempotentGuard.release(messageId);
            log.error("消息处理失败，转入死信队列: messageId={}, productId={}, action={}",
                    messageId, message.getProductId(), message.getAction(), e);
            // requeue=false → 命中队列的 x-dead-letter-exchange，转发到死信队列
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 消费线程内重试，重试耗尽则抛出让调用方转入死信队列。
     */
    private void handleWithRetry(ProductSyncMessage message, String messageId) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                handle(message);
                if (attempt > 1) {
                    log.info("重试成功: messageId={}, attempt={}", messageId, attempt);
                }
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("消费失败: messageId={}, attempt={}/{}, err={}",
                        messageId, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS);
                }
            }
        }
        throw lastError;
    }

    private void handle(ProductSyncMessage message) throws IOException {
        if (ACTION_DELETE.equals(message.getAction())) {
            productIndexWriter.deleteDoc(message.getProductId());
            return;
        }
        if (ACTION_UPSERT.equals(message.getAction())) {
            // 异常不在此处吞掉：由 handleWithRetry 统一重试，最终失败则进死信队列
            productIndexWriter.syncProduct(message.getProductId());
            return;
        }
        log.warn("未知的同步操作类型，忽略: productId={}, action={}",
                message.getProductId(), message.getAction());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
