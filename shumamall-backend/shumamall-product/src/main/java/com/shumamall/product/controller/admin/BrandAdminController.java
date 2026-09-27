package com.shumamall.product.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.product.dto.BrandDTO;
import com.shumamall.product.dto.PageQuery;
import com.shumamall.product.service.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 品牌管理控制器（管理端）。
 */
@Slf4j
@Tag(name = "商品-品牌管理(后台)", description = "管理端品牌的分页查询、创建、更新与删除")
@RestController
@RequestMapping("/api/v1/admin/brand")
@RequiredArgsConstructor
public class BrandAdminController {

    private final BrandService brandService;

    /**
     * 分页查询品牌列表。
     */
    @GetMapping
    public R<PageResult<BrandDTO>> list(@Valid PageQuery query) {
        PageResult<BrandDTO> result = brandService.list(query);
        return R.ok(result);
    }

    /**
     * 创建品牌。
     */
    @PostMapping
    @RequirePermission("brand:create")
    public R<Long> create(@Valid @RequestBody BrandDTO brandDTO) {
        Long id = brandService.create(brandDTO);
        log.info("管理端创建品牌: id={}", id);
        return R.ok(id);
    }

    /**
     * 更新品牌。
     */
    @PutMapping("/{id}")
    @RequirePermission("brand:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BrandDTO brandDTO) {
        brandDTO.setId(id);
        brandService.update(brandDTO);
        return R.ok();
    }

    /**
     * 删除品牌。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("brand:delete")
    public R<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return R.ok();
    }
}
