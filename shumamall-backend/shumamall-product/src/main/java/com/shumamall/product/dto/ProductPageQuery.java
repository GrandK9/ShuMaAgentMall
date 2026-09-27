package com.shumamall.product.dto;

import lombok.Data;

/**
 * 商品分页查询参数。
 */
@Data
public class ProductPageQuery {

    /** 当前页码 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;

    /** 分类ID */
    private Long categoryId;

    /** 品牌ID */
    private Long brandId;

    /** 关键词（搜索商品名称/副标题） */
    private String keyword;

    /** 状态 0-下架 1-上架 */
    private Integer status;

    /** 是否热销 0-否 1-是 */
    private Integer isHot;

    /** 是否新品 0-否 1-是 */
    private Integer isNew;

    /** 是否推荐 0-否 1-是 */
    private Integer isRecommend;

    /** 排序字段（price / sales_volume / created_at） */
    private String sortBy;

    /** 排序方向（asc / desc） */
    private String sortOrder = "desc";
}
