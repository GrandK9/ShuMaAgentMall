package com.shumamall.product.controller.internal;

import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.result.R;
import com.shumamall.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品内部服务控制器（仅微服务间 Feign 调用，不对外开放）。
 */
@Slf4j
@Tag(name = "商品-内部接口", description = "微服务间 Feign 调用的商品 SKU 查询、库存扣减恢复及评论数、销量回写接口")
@RestController
@RequestMapping("/api/v1/internal/product")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    /**
     * 批量查询 SKU 信息。
     *
     * @param skuIds SKU ID 列表，逗号分隔
     * @return SKU 信息列表
     */
    @GetMapping("/skus")
    public R<List<SkuInfoDTO>> getSkuListByIds(@RequestParam List<Long> skuIds) {
        List<SkuInfoDTO> list = productService.getSkuListByIds(skuIds);
        log.debug("内部调用批量查询SKU: skuIds={}, 返回{}条", skuIds, list.size());
        return R.ok(list);
    }

    /**
     * 查询单个 SKU 信息。
     *
     * @param skuId SKU ID
     * @return SKU 信息
     */
    @GetMapping("/sku/{skuId}")
    public R<SkuInfoDTO> getSkuById(@PathVariable Long skuId) {
        SkuInfoDTO dto = productService.getSkuById(skuId);
        log.debug("内部调用查询SKU: skuId={}", skuId);
        return R.ok(dto);
    }

    /**
     * 扣减 SKU 库存。
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @return 操作结果
     */
    @PostMapping("/{skuId}/deductStock")
    public R<Void> deductStock(@PathVariable Long skuId, @RequestParam Integer quantity) {
        productService.deductStock(skuId, quantity);
        log.debug("内部调用扣减库存: skuId={}, quantity={}", skuId, quantity);
        return R.ok();
    }

    /**
     * 恢复 SKU 库存。
     *
     * @param skuId    SKU ID
     * @param quantity 恢复数量
     * @return 操作结果
     */
    @PostMapping("/{skuId}/restoreStock")
    public R<Void> restoreStock(@PathVariable Long skuId, @RequestParam Integer quantity) {
        productService.restoreStock(skuId, quantity);
        log.debug("内部调用恢复库存: skuId={}, quantity={}", skuId, quantity);
        return R.ok();
    }

    /**
     * 调整商品评论数（评论区服务发评/删除时回写）。
     *
     * @param productId 商品ID
     * @param delta     增量（发评 +1，删除 -1）
     * @return 操作结果
     */
    @PutMapping("/{productId}/comment-count")
    public R<Void> updateCommentCount(@PathVariable Long productId, @RequestParam Integer delta) {
        productService.updateCommentCount(productId, delta);
        log.debug("内部调用回写评论数: productId={}, delta={}", productId, delta);
        return R.ok();
    }

    /**
     * 调整商品销量（订单服务支付成功/取消退款时回写，支撑热门商品排序）。
     *
     * @param productId 商品ID
     * @param delta     增量（支付成功 +n，取消退款 -n）
     * @return 操作结果
     */
    @PutMapping("/{productId}/sales-volume")
    public R<Void> updateSalesVolume(@PathVariable Long productId, @RequestParam Integer delta) {
        productService.updateSalesVolume(productId, delta);
        log.debug("内部调用回写销量: productId={}, delta={}", productId, delta);
        return R.ok();
    }
}
