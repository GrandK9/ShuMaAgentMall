package com.shumamall.order.seckill.service;

import com.shumamall.order.seckill.dto.SeckillActivityCreateDTO;
import com.shumamall.order.seckill.dto.SeckillActivityDTO;
import com.shumamall.order.seckill.dto.SeckillRequestDTO;
import com.shumamall.order.seckill.dto.SeckillResultDTO;

import java.util.List;

/**
 * 秒杀服务。
 */
public interface SeckillService {

    /**
     * 创建秒杀活动，初始状态为「未上线」。
     *
     * @param dto 创建参数
     * @return 活动详情（未预热，remainingStock 为 null）
     */
    SeckillActivityDTO createActivity(SeckillActivityCreateDTO dto);

    /**
     * 查询秒杀活动列表。
     *
     * @param status 状态过滤，为空表示全部
     * @return 活动列表（按创建时间倒序）
     */
    List<SeckillActivityDTO> listActivities(Integer status);

    /**
     * 查询秒杀活动详情。
     *
     * @param activityId 活动 ID
     * @return 活动详情
     */
    SeckillActivityDTO getActivity(Long activityId);

    /**
     * 上线活动：把活动库存预热进 Redis。
     * <p>
     * 预热是抢购链路的必要条件 —— Lua 脚本以「库存 key 是否存在」判断活动是否就绪，
     * 未预热的请求直接被拒，不会打穿到数据库。
     *
     * @param activityId 活动 ID
     */
    void onlineActivity(Long activityId);

    /**
     * 下线活动：状态置为已结束，并移除 Redis 中的活动快照，使其不可再抢。
     *
     * @param activityId 活动 ID
     */
    void offlineActivity(Long activityId);

    /**
     * 查询可参与的活动列表（用户端）。
     *
     * @return 未结束的活动，按开始时间升序
     */
    List<SeckillActivityDTO> listOngoingActivities();

    /**
     * 发起抢购：Redis 原子预扣库存 + 占用一人一单资格，落库后发 MQ 异步建单。
     * <p>
     * 本方法只完成「预扣 + 排队」，不等待建单结果，返回排队凭证供前端轮询。
     *
     * @param userId 当前用户
     * @param dto    抢购参数
     * @return 排队中的抢购结果（含 requestId）
     */
    SeckillResultDTO seckill(Long userId, SeckillRequestDTO dto);

    /**
     * 查询抢购结果。
     *
     * @param userId    当前用户
     * @param requestId 抢购凭证
     * @return 当前状态（排队中/已建单/失败/已取消）
     */
    SeckillResultDTO getResult(Long userId, String requestId);

    /**
     * 回补 Redis 预扣（库存加回，按需释放一人一单占位）。
     * <p>
     * 供建单失败、对账回补等场景调用；内部已捕获异常，失败只落错误日志
     * （回补失败不能让"建单失败"这个既成事实变成调用方的新异常）。
     *
     * @param activityId           活动 ID
     * @param userId               用户 ID
     * @param quantity             回补数量
     * @param releaseQualification true=释放资格占位（用户没抢到，允许重抢）；
     *                             false=保留（订单取消，额度退回但本次参与已消耗）
     */
    void rollbackPreheat(Long activityId, Long userId, Integer quantity, boolean releaseQualification);
}
