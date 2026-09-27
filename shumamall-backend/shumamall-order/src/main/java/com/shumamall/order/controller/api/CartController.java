package com.shumamall.order.controller.api;

import com.shumamall.common.result.R;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.order.dto.CartAddDTO;
import com.shumamall.order.dto.CartDTO;
import com.shumamall.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车 API 控制器。
 */
@Tag(name = "订单-购物车", description = "购物车商品的查询、添加、数量与选中状态修改及删除接口")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 获取当前用户的购物车列表。
     *
     * @return 购物车列表
     */
    @Operation(summary = "获取当前用户的购物车列表")
    @GetMapping
    public R<List<CartDTO>> list() {
        Long userId = SecurityContext.getUserId();
        List<CartDTO> cartList = cartService.getCartList(userId);
        return R.ok(cartList);
    }

    /**
     * 添加商品到购物车。
     *
     * @param dto 添加参数
     * @return 操作结果
     */
    @Operation(summary = "添加商品到购物车")
    @PostMapping
    public R<Void> add(@Valid @RequestBody CartAddDTO dto) {
        Long userId = SecurityContext.getUserId();
        cartService.addItem(userId, dto);
        return R.ok();
    }

    /**
     * 更新购物车商品数量。
     *
     * @param skuId    SKU ID
     * @param quantity 新数量（必须 ≥ 1，负数会导致下单时金额为负、扣库存反而加库存）
     * @return 操作结果
     */
    @Operation(summary = "更新购物车商品数量")
    @PutMapping("/{skuId}")
    public R<Void> updateQuantity(@Parameter(description = "SKU ID") @PathVariable Long skuId,
                                  @Parameter(description = "新的商品数量，最小为 1")
                                  @RequestParam @Min(value = 1, message = "数量不能小于1") Integer quantity) {
        Long userId = SecurityContext.getUserId();
        cartService.updateQuantity(userId, skuId, quantity);
        return R.ok();
    }

    /**
     * 更新购物车商品选中状态。
     *
     * @param skuId    SKU ID
     * @param selected 选中状态 0-未选中 1-选中
     * @return 操作结果
     */
    @Operation(summary = "更新购物车商品选中状态")
    @PutMapping("/{skuId}/selected")
    public R<Void> updateSelected(@Parameter(description = "SKU ID") @PathVariable Long skuId,
                                  @Parameter(description = "选中状态：0-未选中 1-选中") @RequestParam Integer selected) {
        Long userId = SecurityContext.getUserId();
        cartService.updateSelected(userId, skuId, selected);
        return R.ok();
    }

    /**
     * 从购物车移除商品。
     *
     * @param skuId SKU ID
     * @return 操作结果
     */
    @Operation(summary = "从购物车移除商品")
    @DeleteMapping("/{skuId}")
    public R<Void> remove(@Parameter(description = "SKU ID") @PathVariable Long skuId) {
        Long userId = SecurityContext.getUserId();
        cartService.removeItem(userId, skuId);
        return R.ok();
    }

    /**
     * 清空购物车。
     *
     * @return 操作结果
     */
    @Operation(summary = "清空购物车")
    @DeleteMapping
    public R<Void> clear() {
        Long userId = SecurityContext.getUserId();
        cartService.clearCart(userId);
        return R.ok();
    }
}
