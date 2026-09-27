package com.shumamall.product.controller;

import com.shumamall.common.result.R;
import com.shumamall.product.dto.CategoryDTO;
import com.shumamall.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类控制器（用户端/公共）。
 */
@Tag(name = "商品-分类", description = "商品分类树公开查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取分类树（公开接口）。
     */
    @Operation(summary = "获取分类树（公开接口）")
    @GetMapping("/tree")
    public R<List<CategoryDTO>> tree() {
        List<CategoryDTO> tree = categoryService.listTree();
        return R.ok(tree);
    }
}
