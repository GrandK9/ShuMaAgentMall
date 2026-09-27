package com.shumamall.order.seckill.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 秒杀建单消息生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${shumamall.seckill.mq.exchange}")
    private String exchange;

    @Value("${shumamall.seckill.mq.routing-key}")
    private String routingKey;

    /**
     * 发送建单消息。
     * <p>
     * 发送失败只记日志、不往上抛：调用方此时 Redis 预扣已成功、资格记录已落库，
     * 把请求判为失败会让用户以为没抢到而重复抢；而记录仍是「待建单」，
     * 对账任务会在阈值后重投 —— 本地消息表的存在意义正是把"发消息"这一步做成可重试的。
     *
     * @param message 建单消息
     */
    public void send(SeckillMessage message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("秒杀建单消息已投递: requestId={}, activityId={}", message.getRequestId(), message.getActivityId());
        } catch (Exception e) {
            log.error("秒杀建单消息投递失败，等待对账任务重投: requestId={}", message.getRequestId(), e);
        }
    }
}
