package com.shumamall.product.controller;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.product.dto.ProductPageQuery;
import com.shumamall.product.dto.ProductVO;
import com.shumamall.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品控制器（用户端）。
 */
@Tag(name = "商品-用户端", description = "用户端商品列表与商品详情查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/user/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 用户端分页查询商品列表（仅已上架）。
     */
    @Operation(summary = "用户端分页查询商品列表（仅已上架）")
    @GetMapping
    public R<PageResult<ProductVO>> list(@Valid ProductPageQuery query) {
        PageResult<ProductVO> result = productService.getUserFacingList(query);
        return R.ok(result);
    }

    /**
     * 用户端查询商品详情。
     */
    @Operation(summary = "用户端查询商品详情")
    @GetMapping("/{id}")
    public R<ProductVO> detail(@Parameter(description = "商品 ID") @PathVariable Long id) {
        ProductVO productVO = productService.getUserFacingDetail(id);
        return R.ok(productVO);
    }
}
