package com.shumamall.order.api;

import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务 Feign 客户端。
 * <p>
 * 刻意<b>不配</b> fallbackFactory，尽管公共配置已开启
 * {@code spring.cloud.openfeign.circuitbreaker.enabled}（见 shumamall-common.yaml）。
 * 原因：deductStock 走的是「下单扣库存」关键路径，全局事务（Seata AT）靠抛异常触发回滚。
 * 一旦挂上 fallbackFactory，熔断/超时会被吞成 R.failed <b>返回值</b>，而调用方
 * OrderServiceImpl 只 catch 异常、不检查返回值 —— 扣库存失败被静默忽略，订单照样创建（超卖），
 * Seata 也拿不到异常不会回滚。因此宁可让 NoFallbackAvailableException 抛出去，
 * 由 catch 兜住转成 STOCK_INSUFFICIENT，保证 fail-closed。
 * 同理 restoreStock / updateSalesVolume 也都是「必须成功或显式失败」的写操作。
 */
@FeignClient("shumamall-product")
public interface ProductFeignClient {

    /**
     * 批量查询 SKU 信息。
     *
     * @param skuIds SKU ID 列表，逗号分隔
     * @return SKU 信息列表
     */
    @GetMapping("/api/v1/internal/product/skus")
    R<List<SkuInfoDTO>> getSkuListByIds(@RequestParam("skuIds") List<Long> skuIds);

    /**
     * 查询单个 SKU 信息。
     *
     * @param skuId SKU ID
     * @return SKU 信息
     */
    @GetMapping("/api/v1/internal/product/sku/{skuId}")
    R<SkuInfoDTO> getSkuById(@PathVariable("skuId") Long skuId);

    /**
     * 扣减 SKU 库存。
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @return 操作结果
     */
    @PostMapping("/api/v1/internal/product/{skuId}/deductStock")
    R<Void> deductStock(@PathVariable("skuId") Long skuId, @RequestParam("quantity") Integer quantity);

    /**
     * 恢复 SKU 库存。
     *
     * @param skuId    SKU ID
     * @param quantity 恢复数量
     * @return 操作结果
     */
    @PostMapping("/api/v1/internal/product/{skuId}/restoreStock")
    R<Void> restoreStock(@PathVariable("skuId") Long skuId, @RequestParam("quantity") Integer quantity);

    /**
     * 调整商品销量（支付成功 / 取消退款时回写，支撑热门商品排序）。
     *
     * @param productId 商品 ID
     * @param delta     增量（支付成功 +n，取消退款 -n）
     * @return 操作结果
     */
    @PutMapping("/api/v1/internal/product/{productId}/sales-volume")
    R<Void> updateSalesVolume(@PathVariable("productId") Long productId, @RequestParam("delta") Integer delta);
}
