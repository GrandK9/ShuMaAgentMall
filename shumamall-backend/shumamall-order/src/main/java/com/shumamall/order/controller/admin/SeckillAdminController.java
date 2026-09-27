package com.shumamall.order.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.R;
import com.shumamall.order.seckill.dto.SeckillActivityCreateDTO;
import com.shumamall.order.seckill.dto.SeckillActivityDTO;
import com.shumamall.order.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀活动管理控制器（管理端）。
 * <p>
 * 权限点复用订单域的 {@code order:view} / {@code order:edit}：秒杀活动落在 order 服务内、
 * 其产物就是普通订单，沿用订单权限即可，不必为此新增权限点与角色授权数据。
 */
@Slf4j
@Tag(name = "秒杀-活动管理(后台)", description = "管理端秒杀活动的创建、查询与上下线（上线即把库存预热进 Redis）")
@RestController
@RequestMapping("/api/v1/admin/seckill")
@RequiredArgsConstructor
public class SeckillAdminController {

    private final SeckillService seckillService;

    /**
     * 创建秒杀活动（初始状态为未上线）。
     *
     * @param dto 创建参数
     * @return 活动详情
     */
    @Operation(summary = "创建秒杀活动")
    @PostMapping
    @RequirePermission("order:edit")
    public R<SeckillActivityDTO> create(@Valid @RequestBody SeckillActivityCreateDTO dto) {
        SeckillActivityDTO activity = seckillService.createActivity(dto);
        log.info("管理端创建秒杀活动: id={}, name={}", activity.getId(), activity.getName());
        return R.ok(activity);
    }

    /**
     * 查询秒杀活动列表。
     *
     * @param status 状态过滤（可选）
     * @return 活动列表
     */
    @Operation(summary = "秒杀活动列表")
    @GetMapping
    @RequirePermission("order:view")
    public R<List<SeckillActivityDTO>> list(@RequestParam(required = false) Integer status) {
        return R.ok(seckillService.listActivities(status));
    }

    /**
     * 查询秒杀活动详情。
     *
     * @param id 活动 ID
     * @return 活动详情
     */
    @Operation(summary = "秒杀活动详情")
    @GetMapping("/{id}")
    @RequirePermission("order:view")
    public R<SeckillActivityDTO> detail(@PathVariable Long id) {
        return R.ok(seckillService.getActivity(id));
    }

    /**
     * 上线活动：把活动库存预热进 Redis，预热成功后才可被抢。
     *
     * @param id 活动 ID
     * @return 操作结果
     */
    @Operation(summary = "上线活动（预热 Redis 库存）")
    @PutMapping("/{id}/online")
    @RequirePermission("order:edit")
    public R<Void> online(@PathVariable Long id) {
        seckillService.onlineActivity(id);
        log.info("管理端上线秒杀活动: id={}", id);
        return R.ok();
    }

    /**
     * 下线活动：状态置为已结束，并移除 Redis 活动快照。
     *
     * @param id 活动 ID
     * @return 操作结果
     */
    @Operation(summary = "下线活动")
    @PutMapping("/{id}/offline")
    @RequirePermission("order:edit")
    public R<Void> offline(@PathVariable Long id) {
        seckillService.offlineActivity(id);
        log.info("管理端下线秒杀活动: id={}", id);
        return R.ok();
    }
}
