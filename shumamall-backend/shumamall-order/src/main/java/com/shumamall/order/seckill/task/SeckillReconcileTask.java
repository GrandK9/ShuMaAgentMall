package com.shumamall.order.seckill.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shumamall.order.seckill.constant.SeckillConstants;
import com.shumamall.order.seckill.dao.SeckillOrderMapper;
import com.shumamall.order.seckill.entity.SeckillOrderEntity;
import com.shumamall.order.seckill.mq.SeckillMessage;
import com.shumamall.order.seckill.mq.SeckillMessageProducer;
import com.shumamall.order.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀对账任务：以 {@code seckill_order} 资格记录为准，兜住异步建单链路里消息未被消费的缺口。
 * <p>
 * 异步化把「扣库存」和「建订单」拆开了，拆开就必然有中间态落单的可能，本任务负责收口：
 * <ol>
 *   <li>记录停在「待建单」超过阈值 → 消息大概率丢了（broker 重启、发送时连接已断），重投；</li>
 *   <li>记录停在「待建单」过久 → 反复重投都建不出来，回补 Redis 预扣并置失败，让用户能重抢。</li>
 * </ol>
 * 订单取消、超时取消、退款时的活动额度回补发生在订单状态变更处，按订单号查资格表处理，
 * 不再在这里扫描全部已建单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillReconcileTask {

    /** 待建单滞留多久算「消息可能丢了」，可以重投 */
    private static final long QUEUED_STALE_MINUTES = 5;

    /** 待建单滞留多久算「重投也建不出来」，放弃并回补 */
    private static final long QUEUED_ABANDON_MINUTES = 30;

    /** 建单中滞留多久算「消费方中断」，告警人工核对 */
    private static final long PROCESSING_STALE_MINUTES = 10;

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillService seckillService;
    private final SeckillMessageProducer producer;

    @Scheduled(fixedRate = 60000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        try {
            requeueStaleQueued(now);
            abandonLongQueued(now);
            warnStuckProcessing(now);
        } catch (Exception e) {
            // 定时任务抛异常会被静默吞掉并停止后续调度，这里兜住，保证下一轮还能跑
            log.error("秒杀对账任务执行异常", e);
        }
    }

    /**
     * 待建单滞留超过 5 分钟：重投消息。
     * <p>
     * 重投后推进 {@code updated_at}（条件是「updated_at 早于阈值」+ 显式刷新），
     * 否则每轮扫描都会重投同一批记录，把队列刷爆。
     */
    private void requeueStaleQueued(LocalDateTime now) {
        List<SeckillOrderEntity> stale = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_QUEUED)
                .lt(SeckillOrderEntity::getUpdatedAt, now.minusMinutes(QUEUED_STALE_MINUTES))
                .ge(SeckillOrderEntity::getUpdatedAt, now.minusMinutes(QUEUED_ABANDON_MINUTES)));

        for (SeckillOrderEntity record : stale) {
            producer.send(toMessage(record));
            touch(record.getId());
            log.warn("秒杀建单消息疑似丢失，已重投: requestId={}, activityId={}, userId={}",
                    record.getRequestId(), record.getActivityId(), record.getUserId());
        }
    }

    /**
     * 待建单滞留超过 30 分钟：不再空转，回补 Redis 预扣并置为失败。
     * <p>
     * 这一步是「少卖」与「用户永远等不到」之间的取舍 —— 30 分钟内多次重投都建不出单，
     * 说明存在需要人工介入的问题，此时把额度还回池子、让用户看到明确的失败结果更可控。
     */
    private void abandonLongQueued(LocalDateTime now) {
        List<SeckillOrderEntity> abandoned = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_QUEUED)
                .lt(SeckillOrderEntity::getUpdatedAt, now.minusMinutes(QUEUED_ABANDON_MINUTES)));

        for (SeckillOrderEntity record : abandoned) {
            seckillService.rollbackPreheat(record.getActivityId(), record.getUserId(),
                    record.getQuantity(), true);
            casStatusWithReason(record.getId(), SeckillConstants.RECORD_QUEUED, SeckillConstants.RECORD_FAILED,
                    "对账重投超时，已回补活动额度");
            log.error("秒杀建单长期未完成，已回补并置失败: requestId={}, activityId={}, userId={}",
                    record.getRequestId(), record.getActivityId(), record.getUserId());
        }
    }

    /**
     * 建单中滞留超过 10 分钟：只告警，不回补也不重投。
     * <p>
     * 该状态说明消费方在「建单完成 → 回填订单号」之间中断了，此时订单可能已经存在：
     * 重投会建出第二笔订单，回补则是对着一笔真实订单释放额度（超卖）。两种自动处理都比
     * 人工核对更危险，因此这里刻意只报错。
     */
    private void warnStuckProcessing(LocalDateTime now) {
        List<SeckillOrderEntity> stuck = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_PROCESSING)
                .lt(SeckillOrderEntity::getUpdatedAt, now.minusMinutes(PROCESSING_STALE_MINUTES)));

        for (SeckillOrderEntity record : stuck) {
            log.error("秒杀资格记录长时间处于建单中，需人工核对订单是否已创建: requestId={}, activityId={}, userId={}, skuId={}",
                    record.getRequestId(), record.getActivityId(), record.getUserId(), record.getSkuId());
        }
    }

    private SeckillMessage toMessage(SeckillOrderEntity record) {
        return new SeckillMessage(record.getRequestId(), record.getActivityId(), record.getUserId(),
                record.getSkuId(), record.getQuantity(), record.getSeckillPrice(),
                record.getAddressId(), record.getRemark());
    }

    /**
     * 刷新记录时间（推进重投时间窗）。
     * <p>
     * 用 {@code update(null, wrapper)} 时不会触发 MyBatis-Plus 的自动填充，这里显式赋值
     * （表结构也带了 ON UPDATE CURRENT_TIMESTAMP，两者一致，不冲突）。
     */
    private void touch(Long recordId) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .set(SeckillOrderEntity::getUpdatedAt, LocalDateTime.now());
        seckillOrderMapper.update(null, update);
    }

    private void casStatusWithReason(Long recordId, int expected, int target, String reason) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .eq(SeckillOrderEntity::getStatus, expected)
                .set(SeckillOrderEntity::getStatus, target)
                .set(SeckillOrderEntity::getFailReason, reason);
        seckillOrderMapper.update(null, update);
    }
}
