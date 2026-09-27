package com.shumamall.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 VO（用户端展示用）。
 */
@Data
@NoArgsConstructor
public class ProductVO {

    private Long id;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称（检索/展示用，由服务端关联分类表回填） */
    private String categoryName;

    /** 品牌ID */
    private Long brandId;

    /** 品牌名称（检索/展示用，由服务端关联品牌表回填） */
    private String brandName;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 商品详情(HTML) */
    private String description;

    /** 主图URL */
    private String mainImage;

    /** 默认价格 */
    private BigDecimal price;

    /** 默认库存 */
    private Integer stock;

    /** 销量 */
    private Integer salesVolume;

    /** 是否新品 0-否 1-是 */
    private Integer isNew;

    /** 是否热销 0-否 1-是 */
    private Integer isHot;

    /** 是否推荐 0-否 1-是 */
    private Integer isRecommend;

    /** SKU列表 */
    private List<ProductSkuDTO> skuList;
}
