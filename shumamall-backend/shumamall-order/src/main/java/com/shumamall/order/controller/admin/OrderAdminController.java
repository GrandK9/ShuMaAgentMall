package com.shumamall.order.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.dto.OrderPageQuery;
import com.shumamall.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理控制器（管理端）。
 */
@Slf4j
@Tag(name = "订单-订单管理(后台)", description = "管理端订单的分页查询、详情查看与状态更新（发货、确认收货等）")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    /**
     * 分页查询全部订单。
     *
     * @param query 查询参数
     * @return 订单分页结果
     */
    @GetMapping
    @RequirePermission("order:view")
    public R<PageResult<OrderDTO>> pageQuery(OrderPageQuery query) {
        PageResult<OrderDTO> result = orderService.adminPageQuery(query);
        return R.ok(result);
    }

    /**
     * 查询订单详情。
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("order:view")
    public R<OrderDTO> detail(@PathVariable Long id) {
        OrderDTO order = orderService.adminGetDetail(id);
        return R.ok(order);
    }

    /**
     * 更新订单状态（发货/确认收货等）。
     *
     * @param id     订单ID
     * @param status 目标状态
     * @param remark 备注（可选）
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @RequirePermission("order:edit")
    public R<Void> updateStatus(@PathVariable Long id,
                                 @RequestParam Integer status,
                                 @RequestParam(required = false) String remark) {
        orderService.updateStatus(id, status, remark);
        log.info("管理端更新订单状态: id={}, status={}", id, status);
        return R.ok();
    }
}
