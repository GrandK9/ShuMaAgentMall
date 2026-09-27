package com.shumamall.search.controller;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.search.dto.ProductSearchVO;
import com.shumamall.search.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品搜索控制器（用户端）。
 * <p>
 * 走 ES 全文检索，ES 不可用时自动降级到商品服务列表接口。
 */
@Tag(name = "搜索-商品检索", description = "基于 ES 的商品全文检索接口，ES 不可用时降级到商品服务列表")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchService productSearchService;

    /**
     * 商品搜索（仅已上架）。
     *
     * @param keyword    关键词（可选）
     * @param categoryId 分类 ID（可选）
     * @param brandIds   品牌 ID 列表（可选，可多选）
     * @param priceMin   最低价（可选）
     * @param priceMax   最高价（可选）
     * @param sortBy     排序：default / sales / price_asc / price_desc / newest
     * @param page       页码，从 1 开始
     * @param size       每页条数
     * @return 搜索结果分页
     */
    @Operation(summary = "商品搜索（仅已上架）")
    @GetMapping("/product")
    public R<PageResult<ProductSearchVO>> search(
                                                 @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) List<Long> brandIds,
                                                 @RequestParam(required = false) Double priceMin,
                                                 @RequestParam(required = false) Double priceMax,
                                                 @RequestParam(defaultValue = "default") String sortBy,
                                                 @RequestParam(defaultValue = "1") @Min(1) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return R.ok(productSearchService.search(keyword, categoryId, brandIds,
                priceMin, priceMax, sortBy, page, size));
    }
}
