package com.shumamall.order.seckill.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.seckill.constant.SeckillConstants;
import com.shumamall.order.seckill.dao.SeckillOrderMapper;
import com.shumamall.order.seckill.entity.SeckillOrderEntity;
import com.shumamall.order.seckill.service.SeckillService;
import com.shumamall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 秒杀建单消费者：把预扣成功的抢购请求异步落成真实订单。
 * <p>
 * 处理约定：
 * <ul>
 *   <li>手动 ack —— 建单成功才确认，异常时不确认并显式 nack 进死信队列；</li>
 *   <li>两层去重 —— Redis 幂等键挡住重复投递，{@code seckill_order} 的「建单中」CAS 挡住
 *       "建单成功但回填订单号前进程挂掉"的重投；</li>
 *   <li>失败回补 —— 建单失败时回补 Redis 预扣并释放资格占位，用户看到失败后可以重抢。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderListener {

    /** 本地重试次数：Feign 抖动、DB 瞬时锁冲突这类瞬时故障不值得直接进死信 */
    private static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_BACKOFF_MS = 300L;

    private static final String IDEMPOTENT_KEY_PREFIX = "seckill:mq:idempotent:";

    private static final Duration IDEMPOTENT_TTL = Duration.ofHours(24);

    private static final int FAIL_REASON_MAX_LENGTH = 200;

    private final SeckillOrderMapper seckillOrderMapper;
    private final OrderService orderService;
    private final SeckillService seckillService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "${shumamall.seckill.mq.queue}")
    public void onMessage(SeckillMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || message.getRequestId() == null) {
            // 脏消息（结构不对）重试多少次都不会好，直接确认丢弃，避免死信队列被垃圾塞满
            log.warn("收到无法处理的秒杀消息，直接确认丢弃: {}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            createOrder(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 回补与置失败已在 createOrder 内部完成，这里只负责把消息送进死信队列保留现场
            log.error("秒杀建单失败，转入死信队列: requestId={}", message.getRequestId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 消费一条建单消息。
     *
     * @param message 建单消息
     * @throws Exception 建单最终失败时抛出，交由调用方 nack 进死信
     */
    private void createOrder(SeckillMessage message) throws Exception {
        String requestId = message.getRequestId();
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + requestId;

        // 幂等第一层：Redis 占位。注意 putIfAbsent 是"先占位再处理"，
        // 处理失败时会在下面把占位删掉，否则死信重放会被自己挡住
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL))) {
            log.info("重复的秒杀消息，跳过: requestId={}", requestId);
            return;
        }

        SeckillOrderEntity record = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getRequestId, requestId));
        if (record == null) {
            // 按当前顺序（先落库再发消息）不该出现；但脏消息要能自愈，不必进死信
            log.warn("秒杀消息找不到资格记录，跳过: requestId={}", requestId);
            return;
        }

        // 幂等第二层：CAS「待建单 → 建单中」。
        // 顺序不能反（先建单再改状态）：那样在"建单成功、状态未改"的窗口内收到重复投递就会建出第二笔订单。
        if (!casStatus(record.getId(), SeckillConstants.RECORD_QUEUED, SeckillConstants.RECORD_PROCESSING)) {
            log.info("资格记录已被处理或正在处理，跳过: requestId={}, status={}", requestId, record.getStatus());
            return;
        }

        try {
            OrderDTO order = createOrderWithRetry(message);
            markCreated(record.getId(), order.getOrderNo());
            log.info("秒杀订单创建成功: requestId={}, orderNo={}", requestId, order.getOrderNo());
        } catch (Exception e) {
            // 建单失败：把 Redis 预扣还回去，并释放资格占位（用户没成，应当允许重抢；
            // 数据库侧的唯一索引允许 FAILED 记录被重新置为待建单，见 SeckillServiceImpl#seckill）
            seckillService.rollbackPreheat(message.getActivityId(), message.getUserId(),
                    message.getQuantity(), true);
            markFailed(record.getId(), e);
            // 释放幂等占位：消息进了死信队列，人工重放时不该被"已处理过"挡住
            stringRedisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    /**
     * 带本地重试的建单。
     *
     * @param message 建单消息
     * @return 建好的订单
     * @throws Exception 重试耗尽后抛出最后一次异常
     */
    private OrderDTO createOrderWithRetry(SeckillMessage message) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return orderService.createSeckillOrder(message.getUserId(), message.getSkuId(), message.getQuantity(),
                        message.getSeckillPrice(), message.getAddressId(), message.getRemark());
            } catch (Exception e) {
                lastError = e;
                log.warn("秒杀建单失败: requestId={}, attempt={}/{}, err={}",
                        message.getRequestId(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS);
                }
            }
        }
        throw lastError;
    }

    /**
     * 条件更新资格记录状态（CAS），把「期望的原状态」写进 where 条件。
     *
     * @param recordId 记录 ID
     * @param expected 期望的原状态
     * @param target   目标状态
     * @return 是否更新成功；false 说明已被并发消费方改走
     */
    private boolean casStatus(Long recordId, int expected, int target) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .eq(SeckillOrderEntity::getStatus, expected)
                .set(SeckillOrderEntity::getStatus, target);
        return seckillOrderMapper.update(null, update) > 0;
    }

    private void markCreated(Long recordId, String orderNo) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .set(SeckillOrderEntity::getOrderNo, orderNo)
                .set(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_CREATED);
        seckillOrderMapper.update(null, update);
    }

    private void markFailed(Long recordId, Exception error) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .set(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_FAILED)
                .set(SeckillOrderEntity::getFailReason, truncate(error.getMessage()));
        seckillOrderMapper.update(null, update);
    }

    /**
     * 截断失败原因以适配列长度（异常信息可能非常长）。
     */
    private String truncate(String reason) {
        if (reason == null) {
            return "建单失败";
        }
        return reason.length() > FAIL_REASON_MAX_LENGTH ? reason.substring(0, FAIL_REASON_MAX_LENGTH) : reason;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
