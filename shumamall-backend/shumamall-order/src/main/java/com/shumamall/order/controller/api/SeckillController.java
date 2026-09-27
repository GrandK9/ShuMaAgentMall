package com.shumamall.order.controller.api;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.order.seckill.dto.SeckillActivityDTO;
import com.shumamall.order.seckill.dto.SeckillRequestDTO;
import com.shumamall.order.seckill.dto.SeckillResultDTO;
import com.shumamall.order.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀控制器（用户端）。
 * <p>
 * 抢购接口是「秒回」语义：只做 Redis 预扣与排队，不阻塞等建单结果，
 * 前端拿 requestId 轮询查结果 —— 同步建单会让请求线程在洪峰下被占满。
 */
@Slf4j
@Tag(name = "秒杀(用户端)", description = "秒杀活动浏览、抢购与抢购结果查询")
@RestController
@RequestMapping("/api/v1/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 秒杀活动列表（未结束的活动）。
     *
     * @return 活动列表
     */
    @Operation(summary = "秒杀活动列表")
    @GetMapping("/activities")
    public R<List<SeckillActivityDTO>> activities() {
        return R.ok(seckillService.listOngoingActivities());
    }

    /**
     * 发起抢购。
     *
     * @param dto 抢购参数
     * @return 排队中的抢购结果（含 requestId）
     */
    @Operation(summary = "发起抢购")
    @PostMapping("/orders")
    public R<SeckillResultDTO> seckill(@Valid @RequestBody SeckillRequestDTO dto) {
        Long userId = SecurityContext.getUserId();
        SeckillResultDTO result = seckillService.seckill(userId, dto);
        log.info("用户抢购成功进入排队: userId={}, activityId={}, requestId={}",
                userId, dto.getActivityId(), result.getRequestId());
        return R.ok(result);
    }

    /**
     * 查询抢购结果（前端轮询）。
     *
     * @param requestId 抢购凭证
     * @return 当前状态
     */
    @Operation(summary = "查询抢购结果")
    @GetMapping("/orders/{requestId}")
    public R<SeckillResultDTO> result(@PathVariable String requestId) {
        Long userId = SecurityContext.getUserId();
        return R.ok(seckillService.getResult(userId, requestId));
    }
}
