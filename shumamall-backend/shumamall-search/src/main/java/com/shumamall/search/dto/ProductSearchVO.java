package com.shumamall.search.dto;

import lombok.Data;

/**
 * 商品搜索结果 VO（前端直接渲染）。
 */
@Data
public class ProductSearchVO {

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 主图 URL */
    private String mainImage;

    /** 默认价格 */
    private Double price;

    /** 销量 */
    private Integer salesVolume;

    /** 分类名称 */
    private String categoryName;

    /** 品牌名称 */
    private String brandName;

    /** ES 相关度评分 */
    private Float score;
}
