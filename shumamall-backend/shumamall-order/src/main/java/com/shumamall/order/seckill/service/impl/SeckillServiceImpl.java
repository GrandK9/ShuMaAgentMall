package com.shumamall.order.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.order.api.ProductFeignClient;
import com.shumamall.order.seckill.constant.SeckillConstants;
import com.shumamall.order.seckill.dao.SeckillActivityMapper;
import com.shumamall.order.seckill.dao.SeckillOrderMapper;
import com.shumamall.order.seckill.dto.SeckillActivityCreateDTO;
import com.shumamall.order.seckill.dto.SeckillActivityDTO;
import com.shumamall.order.seckill.dto.SeckillRequestDTO;
import com.shumamall.order.seckill.dto.SeckillResultDTO;
import com.shumamall.order.seckill.entity.SeckillActivityEntity;
import com.shumamall.order.seckill.entity.SeckillOrderEntity;
import com.shumamall.order.seckill.mq.SeckillMessage;
import com.shumamall.order.seckill.mq.SeckillMessageProducer;
import com.shumamall.order.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 秒杀服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    /** 预扣脚本：库存校验 + 一人一单占位 + 扣减，一次执行内原子完成，杜绝"判断后扣减"之间的超卖窗口 */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = loadScript("lua/seckill_deduct.lua");

    /** 回补脚本：库存加回 + 按需释放资格占位，两者必须一起做 */
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = loadScript("lua/seckill_rollback.lua");

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductFeignClient productFeignClient;
    private final SeckillMessageProducer producer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static DefaultRedisScript<Long> loadScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivityDTO createActivity(SeckillActivityCreateDTO dto) {
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "开始时间必须早于结束时间");
        }

        SkuInfoDTO sku = fetchSku(dto.getSkuId());
        if (sku.getStock() != null && sku.getStock() < dto.getTotalStock()) {
            // 不阻断创建：SKU 可能在活动开始前补货。但上线时会按较小值预热，这里先留下线索
            log.warn("活动库存大于 SKU 当前库存，上线时将按 SKU 库存截断: skuId={}, skuStock={}, activityStock={}",
                    dto.getSkuId(), sku.getStock(), dto.getTotalStock());
        }

        SeckillActivityEntity entity = new SeckillActivityEntity();
        entity.setName(dto.getName());
        entity.setSkuId(dto.getSkuId());
        entity.setProductId(dto.getProductId());
        entity.setSeckillPrice(dto.getSeckillPrice());
        entity.setTotalStock(dto.getTotalStock());
        entity.setPerUserLimit(dto.getPerUserLimit() == null ? 1 : dto.getPerUserLimit());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        // 新建一律「未上线」：上线动作才把库存预热进 Redis，避免活动还没配好就被抢
        entity.setStatus(SeckillConstants.ACTIVITY_OFFLINE);
        activityMapper.insert(entity);

        log.info("秒杀活动已创建: id={}, name={}, skuId={}, seckillPrice={}, totalStock={}",
                entity.getId(), entity.getName(), entity.getSkuId(), entity.getSeckillPrice(), entity.getTotalStock());
        return toDTO(entity);
    }

    @Override
    public List<SeckillActivityDTO> listActivities(Integer status) {
        LambdaQueryWrapper<SeckillActivityEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SeckillActivityEntity::getStatus, status);
        }
        wrapper.orderByDesc(SeckillActivityEntity::getCreatedAt);
        return activityMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SeckillActivityDTO getActivity(Long activityId) {
        return toDTO(requireActivity(activityId));
    }

    @Override
    public void onlineActivity(Long activityId) {
        SeckillActivityEntity activity = requireActivity(activityId);

        // 先把耗时的远程调用做完再改状态：Feign 调用期间活动仍是「未上线」，
        // 不会出现"状态已上线但库存还没预热"的中间态。
        SkuInfoDTO sku = fetchSku(activity.getSkuId());
        int skuStock = sku.getStock() == null ? 0 : sku.getStock();
        // 活动库存是「从 SKU 库存里划出来的一块额度」，配置值可能大于 SKU 实际库存。
        // 按较大值预热会让超出的那部分在异步建单时必然失败（用户白排队再被回补），故取较小值。
        int preheatStock = Math.min(activity.getTotalStock(), skuStock);
        if (preheatStock <= 0) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "SKU 当前无可用库存，无法上线");
        }

        // 条件更新（CAS）抢占「未上线 → 已上线」：重复点上线会重新预热 Redis 库存，
        // 把活动进行期间已经扣掉的量又盖回去（等于凭空放量 → 超卖）。
        if (!casUpdateStatus(activityId, SeckillConstants.ACTIVITY_OFFLINE, SeckillConstants.ACTIVITY_ONLINE)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "活动当前状态不允许上线");
        }

        long ttlSeconds = redisTtlSeconds(activity);
        try {
            redisTemplate.opsForValue().set(SeckillConstants.STOCK_KEY_PREFIX + activityId,
                    String.valueOf(preheatStock), Duration.ofSeconds(ttlSeconds));

            // 活动快照：抢购路径只读 Redis 快照，避免每个抢购请求都回查数据库
            activity.setStatus(SeckillConstants.ACTIVITY_ONLINE);
            redisTemplate.opsForValue().set(SeckillConstants.ACTIVITY_KEY_PREFIX + activityId,
                    toJson(activity), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            // 预热失败必须把状态回滚：否则活动看起来「已上线」却抢不动，
            // 而再次点上线又会被上面的 CAS 拦住，只能手工改库。
            redisTemplate.delete(List.of(SeckillConstants.STOCK_KEY_PREFIX + activityId,
                    SeckillConstants.ACTIVITY_KEY_PREFIX + activityId));
            casUpdateStatus(activityId, SeckillConstants.ACTIVITY_ONLINE, SeckillConstants.ACTIVITY_OFFLINE);
            log.error("秒杀活动预热失败，已回滚上线状态: activityId={}", activityId, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "活动预热失败，请稍后重试");
        }

        log.info("秒杀活动已上线: activityId={}, preheatStock={}, ttlSeconds={}", activityId, preheatStock, ttlSeconds);
    }

    @Override
    public void offlineActivity(Long activityId) {
        requireActivity(activityId);

        if (!casUpdateStatus(activityId, SeckillConstants.ACTIVITY_ONLINE, SeckillConstants.ACTIVITY_FINISHED)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "只有已上线的活动可以下线");
        }

        // 只删活动快照：抢购路径以快照作为「活动可抢」的入口，删掉即无法再抢。
        // 库存 key 刻意保留到 TTL 自然过期 —— 此时可能仍有排队中的资格记录需要按它回补库存。
        try {
            redisTemplate.delete(SeckillConstants.ACTIVITY_KEY_PREFIX + activityId);
        } catch (Exception e) {
            log.warn("删除秒杀活动快照失败（活动状态已下线，快照到期自动失效）: activityId={}", activityId, e);
        }
        log.info("秒杀活动已下线: activityId={}", activityId);
    }

    @Override
    public List<SeckillActivityDTO> listOngoingActivities() {
        LambdaQueryWrapper<SeckillActivityEntity> wrapper = new LambdaQueryWrapper<SeckillActivityEntity>()
                .ne(SeckillActivityEntity::getStatus, SeckillConstants.ACTIVITY_FINISHED)
                .gt(SeckillActivityEntity::getEndTime, LocalDateTime.now())
                .orderByAsc(SeckillActivityEntity::getStartTime);
        return activityMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SeckillResultDTO seckill(Long userId, SeckillRequestDTO dto) {
        Long activityId = dto.getActivityId();
        SeckillActivityEntity activity = loadActivitySnapshot(activityId);

        // 状态与时间窗是「整个活动能不能抢」，与库存无关，用快照判断即可，不必进 Lua
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStatus() == null || activity.getStatus() != SeckillConstants.ACTIVITY_ONLINE) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "活动未上线");
        }
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "活动尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "活动已结束");
        }

        // 当前实现每人限购 1 件：Lua 用 SETNX 占位，一个 key 只能表达「参与过一次」
        int quantity = 1;
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String userKey = SeckillConstants.USER_KEY_PREFIX + activityId + ":" + userId;

        Long executed = redisTemplate.execute(DEDUCT_SCRIPT,
                List.of(SeckillConstants.STOCK_KEY_PREFIX + activityId, userKey),
                String.valueOf(quantity), String.valueOf(SeckillConstants.USER_KEY_TTL_SECONDS));
        long luaResult = executed == null ? SeckillConstants.LUA_NOT_PREHEATED : executed;
        if (luaResult == SeckillConstants.LUA_NOT_PREHEATED) {
            // 库存 key 不存在 ≠ 抢光了：也可能是预热数据被清理或过期，提示语不能误导用户
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "活动未就绪，请稍后重试");
        }
        if (luaResult == SeckillConstants.LUA_STOCK_NOT_ENOUGH) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "已被抢完");
        }
        if (luaResult == SeckillConstants.LUA_DUPLICATE) {
            throw new BusinessException(ResultCode.CONFLICT, "您已参与过该活动");
        }

        SeckillOrderEntity record = buildRecord(requestId, userId, activity, quantity, dto);
        try {
            // 先落库再发 MQ，顺序不能反：反过来的话消息可能先到消费者，消费者查不到资格记录只能丢弃，
            // 而 Redis 库存已经白扣 —— 用户既没有订单，也没法重抢
            seckillOrderMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 撞 uk_activity_user：数据库记得该用户在本活动参与过（Redis 资格占位丢过，或上次建单失败）
            SeckillOrderEntity existing = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderEntity>()
                    .eq(SeckillOrderEntity::getActivityId, activityId)
                    .eq(SeckillOrderEntity::getUserId, userId));
            // 只有「上次建单失败」的记录允许重抢：那时额度已回补、资格已释放，这条记录本身已作废。
            // 已建单/已取消/正在处理的一律拒绝，否则一人一单形同虚设
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() == SeckillConstants.RECORD_FAILED
                    && requeueForRetry(existing.getId(), requestId, dto)) {
                producer.send(toMessage(record));
                log.info("上次建单失败的记录已重置为待建单并重投: requestId={}, activityId={}, userId={}",
                        requestId, activityId, userId);
                return queuedResult(requestId);
            }
            // 其余情况本次抢购作废：把刚扣掉的库存还回去，资格占位保留（该用户本次参与已消耗）
            rollbackPreheat(activityId, userId, quantity, false);
            throw new BusinessException(ResultCode.CONFLICT, "您已参与过该活动");
        }

        producer.send(toMessage(record));
        log.info("秒杀预扣成功: requestId={}, activityId={}, userId={}", requestId, activityId, userId);
        return queuedResult(requestId);
    }

    @Override
    public SeckillResultDTO getResult(Long userId, String requestId) {
        SeckillOrderEntity record = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getRequestId, requestId));
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "抢购记录不存在");
        }
        // 归属校验：requestId 是 UUID 不易猜，但仍按「只能查自己的记录」处理
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看他人的抢购记录");
        }
        return toResultDTO(record);
    }

    @Override
    public void rollbackPreheat(Long activityId, Long userId, Integer quantity, boolean releaseQualification) {
        try {
            Long stock = redisTemplate.execute(ROLLBACK_SCRIPT,
                    List.of(SeckillConstants.STOCK_KEY_PREFIX + activityId,
                            SeckillConstants.USER_KEY_PREFIX + activityId + ":" + userId),
                    String.valueOf(quantity), releaseQualification ? "1" : "0");
            if (stock == null || stock == SeckillConstants.LUA_NOT_PREHEATED) {
                // 库存 key 已不存在（活动数据被清理/过期）时加不回去。这不是丢账：
                // DB 侧 SKU 库存在订单超时取消时还会回补，Redis 这层只是活动额度的展示与准入
                log.warn("秒杀库存回补跳过（库存 key 不存在）: activityId={}, userId={}", activityId, userId);
                return;
            }
            log.info("秒杀库存已回补: activityId={}, userId={}, quantity={}, remainingStock={}, releaseQualification={}",
                    activityId, userId, quantity, stock, releaseQualification);
        } catch (Exception e) {
            // 回补失败不能让调用方（建单失败的消费者、对账任务）跟着炸：
            // 建单失败这个既成事实已经落库，回补是补偿动作，失败落错误日志待人工核对
            log.error("秒杀库存回补失败: activityId={}, userId={}, quantity={}", activityId, userId, quantity, e);
        }
    }

    /**
     * 读活动快照：抢购是热点路径，先用 Redis 快照挡住重复查库；快照缺失时回源数据库并重建。
     *
     * @param activityId 活动 ID
     * @return 活动信息
     */
    private SeckillActivityEntity loadActivitySnapshot(Long activityId) {
        try {
            String json = redisTemplate.opsForValue().get(SeckillConstants.ACTIVITY_KEY_PREFIX + activityId);
            if (json != null) {
                return objectMapper.readValue(json, SeckillActivityEntity.class);
            }
        } catch (Exception e) {
            // 快照读失败不阻断：回源数据库即可。Redis 抖动不该让整个秒杀入口不可用
            log.warn("读取活动快照失败，回源数据库: activityId={}, err={}", activityId, e.getMessage());
        }
        SeckillActivityEntity activity = requireActivity(activityId);
        cacheActivitySnapshot(activity);
        return activity;
    }

    /**
     * 回源后把快照写回 Redis。
     * <p>
     * 只缓存「已上线」的活动：未上线时写进去没有意义，还会掩盖后续上线时的预热结果。
     */
    private void cacheActivitySnapshot(SeckillActivityEntity activity) {
        if (activity.getStatus() == null || activity.getStatus() != SeckillConstants.ACTIVITY_ONLINE) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(SeckillConstants.ACTIVITY_KEY_PREFIX + activity.getId(),
                    toJson(activity), Duration.ofSeconds(redisTtlSeconds(activity)));
        } catch (Exception e) {
            log.warn("回写活动快照失败: activityId={}, err={}", activity.getId(), e.getMessage());
        }
    }

    /**
     * 允许重抢：把上次建单失败的记录重新置为「待建单」。
     * <p>
     * 复用原记录而不是插新记录 —— {@code uk_activity_user} 决定了同一活动同一用户只有一行，
     * 插新记录必然再次撞唯一键。requestId 换成本次请求的，旧 requestId 随之作废。
     *
     * @param recordId  原记录 ID
     * @param requestId 本次请求的 requestId
     * @param dto       本次请求参数
     * @return 是否成功重置；false 表示该记录已被并发请求推进，本次应判为「已参与」
     */
    private boolean requeueForRetry(Long recordId, String requestId, SeckillRequestDTO dto) {
        LambdaUpdateWrapper<SeckillOrderEntity> update = new LambdaUpdateWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getId, recordId)
                .eq(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_FAILED)
                .set(SeckillOrderEntity::getRequestId, requestId)
                .set(SeckillOrderEntity::getAddressId, dto.getAddressId())
                .set(SeckillOrderEntity::getRemark, dto.getRemark())
                .set(SeckillOrderEntity::getFailReason, null)
                .set(SeckillOrderEntity::getStatus, SeckillConstants.RECORD_QUEUED);
        return seckillOrderMapper.update(null, update) > 0;
    }

    private SeckillOrderEntity buildRecord(String requestId, Long userId, SeckillActivityEntity activity,
                                           int quantity, SeckillRequestDTO dto) {
        SeckillOrderEntity record = new SeckillOrderEntity();
        record.setRequestId(requestId);
        record.setActivityId(activity.getId());
        record.setUserId(userId);
        record.setSkuId(activity.getSkuId());
        record.setQuantity(quantity);
        record.setSeckillPrice(activity.getSeckillPrice());
        record.setAddressId(dto.getAddressId());
        record.setRemark(dto.getRemark());
        record.setStatus(SeckillConstants.RECORD_QUEUED);
        return record;
    }

    private SeckillMessage toMessage(SeckillOrderEntity record) {
        return new SeckillMessage(record.getRequestId(), record.getActivityId(), record.getUserId(),
                record.getSkuId(), record.getQuantity(), record.getSeckillPrice(),
                record.getAddressId(), record.getRemark());
    }

    private SeckillResultDTO queuedResult(String requestId) {
        return new SeckillResultDTO(requestId, SeckillConstants.RECORD_QUEUED, "排队中", null,
                "抢购成功，正在为您生成订单");
    }

    private SeckillResultDTO toResultDTO(SeckillOrderEntity record) {
        SeckillResultDTO dto = new SeckillResultDTO();
        dto.setRequestId(record.getRequestId());
        dto.setStatus(record.getStatus());
        dto.setOrderNo(record.getOrderNo());
        if (record.getStatus() == null
                || record.getStatus() == SeckillConstants.RECORD_QUEUED
                || record.getStatus() == SeckillConstants.RECORD_PROCESSING) {
            dto.setStatusLabel("排队中");
            dto.setMessage("正在为您生成订单，请稍候");
        } else if (record.getStatus() == SeckillConstants.RECORD_CREATED) {
            dto.setStatusLabel("已建单");
            dto.setMessage("抢购成功，请尽快完成支付");
        } else if (record.getStatus() == SeckillConstants.RECORD_CANCELLED) {
            dto.setStatusLabel("已取消");
            dto.setMessage("订单已取消，活动额度已退回");
        } else {
            dto.setStatusLabel("失败");
            dto.setMessage(record.getFailReason() == null ? "抢购失败，活动额度已退回" : record.getFailReason());
        }
        return dto;
    }

    /**
     * 条件更新活动状态（CAS），把「期望的原状态」写进 where 条件。
     *
     * @param activityId 活动 ID
     * @param expected   期望的原状态
     * @param target     目标状态
     * @return 是否更新成功；false 说明状态已被并发请求改走
     */
    private boolean casUpdateStatus(Long activityId, int expected, int target) {
        LambdaUpdateWrapper<SeckillActivityEntity> update = new LambdaUpdateWrapper<SeckillActivityEntity>()
                .eq(SeckillActivityEntity::getId, activityId)
                .eq(SeckillActivityEntity::getStatus, expected)
                .set(SeckillActivityEntity::getStatus, target);
        return activityMapper.update(null, update) > 0;
    }

    /**
     * Redis 中活动数据的存活时间：覆盖到活动结束之后 1 小时。
     * <p>
     * 不用固定值：活动可能持续数天，固定 TTL 会在活动中途过期，导致库存 key 消失、
     * 抢购请求全部被判为「未预热」。结束后保留 1 小时是留给对账与库存回补的时间窗，
     * 再之后由 {@link SeckillConstants#ACTIVITY_KEY_TTL_SECONDS} 兜底收敛（避免结束时间填得过近时秒过期）。
     *
     * @param activity 活动
     * @return TTL（秒）
     */
    private long redisTtlSeconds(SeckillActivityEntity activity) {
        long untilEnd = Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds() + 3600;
        return Math.max(untilEnd, SeckillConstants.ACTIVITY_KEY_TTL_SECONDS);
    }

    private SeckillActivityEntity requireActivity(Long activityId) {
        SeckillActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        return activity;
    }

    private SkuInfoDTO fetchSku(Long skuId) {
        try {
            R<SkuInfoDTO> result = productFeignClient.getSkuById(skuId);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "SKU 不存在: " + skuId);
            }
            return result.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询 SKU 失败: skuId={}", skuId, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "商品服务调用失败");
        }
    }

    private SeckillActivityDTO toDTO(SeckillActivityEntity entity) {
        SeckillActivityDTO dto = new SeckillActivityDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSkuId(entity.getSkuId());
        dto.setProductId(entity.getProductId());
        dto.setSeckillPrice(entity.getSeckillPrice());
        dto.setTotalStock(entity.getTotalStock());
        dto.setPerUserLimit(entity.getPerUserLimit());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setStatus(entity.getStatus());
        dto.setStatusLabel(statusLabel(entity.getStatus()));
        dto.setRemainingStock(readRemainingStock(entity.getId()));
        return dto;
    }

    /**
     * 读 Redis 剩余库存，未预热或 Redis 不可用时返回 null（而不是 0）。
     */
    private Integer readRemainingStock(Long activityId) {
        try {
            String value = redisTemplate.opsForValue().get(SeckillConstants.STOCK_KEY_PREFIX + activityId);
            return value == null ? null : Integer.valueOf(value);
        } catch (Exception e) {
            log.warn("读取秒杀剩余库存失败: activityId={}, err={}", activityId, e.getMessage());
            return null;
        }
    }

    private String toJson(SeckillActivityEntity activity) {
        try {
            return objectMapper.writeValueAsString(activity);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "活动快照序列化失败");
        }
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SeckillConstants.ACTIVITY_OFFLINE -> "未上线";
            case SeckillConstants.ACTIVITY_ONLINE -> "已上线";
            case SeckillConstants.ACTIVITY_FINISHED -> "已结束";
            default -> "未知";
        };
    }
}
