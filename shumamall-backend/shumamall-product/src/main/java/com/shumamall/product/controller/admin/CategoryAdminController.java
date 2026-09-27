package com.shumamall.product.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.R;
import com.shumamall.product.dto.CategoryDTO;
import com.shumamall.product.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品分类管理控制器（管理端）。
 */
@Slf4j
@Tag(name = "商品-分类管理(后台)", description = "管理端商品分类的创建、更新与删除")
@RestController
@RequestMapping("/api/v1/admin/category")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryService categoryService;

    /**
     * 创建分类。
     */
    @PostMapping
    @RequirePermission("category:create")
    public R<Long> create(@Valid @RequestBody CategoryDTO categoryDTO) {
        Long id = categoryService.create(categoryDTO);
        log.info("管理端创建分类: id={}", id);
        return R.ok(id);
    }

    /**
     * 更新分类。
     */
    @PutMapping("/{id}")
    @RequirePermission("category:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO categoryDTO) {
        categoryDTO.setId(id);
        categoryService.update(categoryDTO);
        return R.ok();
    }

    /**
     * 删除分类。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("category:delete")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
