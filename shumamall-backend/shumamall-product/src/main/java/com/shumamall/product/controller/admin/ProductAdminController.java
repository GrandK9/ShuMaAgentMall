package com.shumamall.product.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.product.dto.ProductDTO;
import com.shumamall.product.dto.ProductPageQuery;
import com.shumamall.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理控制器（管理端）。
 */
@Slf4j
@Tag(name = "商品-商品管理(后台)", description = "管理端商品的分页查询、详情、创建、更新、上下架、删除与库存维护")
@RestController
@RequestMapping("/api/v1/admin/product")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    /**
     * 分页查询商品列表。
     */
    @GetMapping
    public R<PageResult<ProductDTO>> pageQuery(@Valid ProductPageQuery query) {
        PageResult<ProductDTO> result = productService.pageQuery(query);
        return R.ok(result);
    }

    /**
     * 查询商品详情。
     */
    @GetMapping("/{id}")
    public R<ProductDTO> getById(@PathVariable Long id) {
        ProductDTO productDTO = productService.getById(id);
        return R.ok(productDTO);
    }

    /**
     * 创建商品（含SKU）。
     */
    @PostMapping
    @RequirePermission("product:create")
    public R<Long> create(@Valid @RequestBody ProductDTO productDTO) {
        Long id = productService.create(productDTO);
        log.info("管理端创建商品: id={}", id);
        return R.ok(id);
    }

    /**
     * 更新商品（含SKU）。
     */
    @PutMapping("/{id}")
    @RequirePermission("product:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProductDTO productDTO) {
        productDTO.setId(id);
        productService.update(productDTO);
        return R.ok();
    }

    /**
     * 更新商品上下架状态。
     */
    @PutMapping("/{id}/status")
    @RequirePermission("product:publish")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateStatus(id, status);
        log.info("管理端更新商品状态: id={}, status={}", id, status);
        return R.ok();
    }

    /**
     * 删除商品。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("product:delete")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    /**
     * 更新商品默认库存。
     *
     * @param id    商品ID
     * @param stock 新库存数量
     * @return 操作结果
     */
    @PutMapping("/{id}/stock")
    @RequirePermission("product:edit")
    public R<Void> updateStock(@PathVariable Long id, @RequestParam Integer stock) {
        productService.updateProductStock(id, stock);
        log.info("管理端更新商品库存: id={}, stock={}", id, stock);
        return R.ok();
    }

    /**
     * 商品总数（供管理端仪表盘聚合调用）。
     *
     * @return 商品总数
     */
    @GetMapping("/count")
    public R<Long> count() {
        return R.ok(productService.countProducts());
    }
}
