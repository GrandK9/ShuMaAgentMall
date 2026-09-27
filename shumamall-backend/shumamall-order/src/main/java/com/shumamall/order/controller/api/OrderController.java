package com.shumamall.order.controller.api;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.order.dto.OrderCreateDTO;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.dto.OrderPageQuery;
import com.shumamall.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单 API 控制器。
 */
@Tag(name = "订单-用户端", description = "用户端订单的创建、查询与取消接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单。
     *
     * @param dto 创建订单参数
     * @return 订单详情
     */
    @Operation(summary = "创建订单")
    @PostMapping
    public R<OrderDTO> create(@Valid @RequestBody OrderCreateDTO dto) {
        Long userId = SecurityContext.getUserId();
        OrderDTO order = orderService.createOrder(userId, dto);
        log.info("用户下单成功: userId={}, orderNo={}", userId, order.getOrderNo());
        return R.ok(order);
    }

    /**
     * 查询订单详情。
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @Operation(summary = "查询订单详情")
    @GetMapping("/{id}")
    public R<OrderDTO> detail(@Parameter(description = "订单 ID") @PathVariable Long id) {
        Long userId = SecurityContext.getUserId();
        OrderDTO order = orderService.getById(id, userId);
        return R.ok(order);
    }

    /**
     * 分页查询我的订单。
     *
     * @param query 查询参数
     * @return 订单分页结果
     */
    @Operation(summary = "分页查询我的订单")
    @GetMapping
    public R<PageResult<OrderDTO>> pageQuery(OrderPageQuery query) {
        Long userId = SecurityContext.getUserId();
        query.setUserId(userId);
        PageResult<OrderDTO> result = orderService.pageQuery(query);
        return R.ok(result);
    }

    /**
     * 取消订单。
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @Operation(summary = "取消订单")
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@Parameter(description = "订单 ID") @PathVariable Long id) {
        Long userId = SecurityContext.getUserId();
        orderService.cancelOrder(id, userId);
        log.info("用户取消订单: id={}, userId={}", id, userId);
        return R.ok();
    }

    /**
     * 确认收货。
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @Operation(summary = "确认收货")
    @PutMapping("/{id}/receive")
    public R<Void> receive(@Parameter(description = "订单 ID") @PathVariable Long id) {
        Long userId = SecurityContext.getUserId();
        orderService.receiveOrder(id, userId);
        log.info("用户确认收货: id={}, userId={}", id, userId);
        return R.ok();
    }
}
