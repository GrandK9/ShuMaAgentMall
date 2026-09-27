package com.shumamall.search.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.search.dto.ProductSearchVO;

import java.util.List;

/**
 * 商品搜索服务接口。
 */
public interface ProductSearchService {

    /**
     * 商品搜索（仅已上架）。
     *
     * @param keyword    关键词（名称/副标题/描述）
     * @param categoryId 分类 ID（可选）
     * @param brandIds   品牌 ID 列表（可选）
     * @param priceMin   最低价（可选）
     * @param priceMax   最高价（可选）
     * @param sortBy     排序：default / sales / price_asc / price_desc / newest
     * @param page       页码，从 1 开始
     * @param size       每页条数
     * @return 搜索结果分页
     */
    PageResult<ProductSearchVO> search(String keyword, Long categoryId, List<Long> brandIds,
                                       Double priceMin, Double priceMax, String sortBy,
                                       int page, int size);
}
