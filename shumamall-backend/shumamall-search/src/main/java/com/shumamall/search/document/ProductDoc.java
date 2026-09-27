package com.shumamall.search.document;

import lombok.Data;

/**
 * 商品 ES 索引文档模型（shumamall_product_search）。
 * <p>
 * 字段与 {@code ProductIndexService} 创建的索引 mapping 保持一致，
 * ES 客户端按该 POJO 直接序列化/反序列化文档。
 */
@Data
public class ProductDoc {

    /** 商品 ID（与 MySQL 商品主键一致） */
    private Long id;

    /** 商品名称（全文检索） */
    private String name;

    /** 副标题/卖点（全文检索） */
    private String subtitle;

    /** 商品详情描述（全文检索） */
    private String description;

    /** 分类 ID（精确过滤） */
    private Long categoryId;

    /** 分类名称（展示） */
    private String categoryName;

    /** 品牌 ID（精确过滤） */
    private Long brandId;

    /** 品牌名称（展示） */
    private String brandName;

    /** 默认价格 */
    private Double price;

    /** 销量（排序） */
    private Integer salesVolume;

    /** 状态 0-下架 1-上架 */
    private Integer status;

    /** 是否新品 */
    private Boolean isNew;

    /** 是否热销 */
    private Boolean isHot;

    /** 主图 URL */
    private String mainImage;

    /** 创建时间（ISO-8601，排序用） */
    private String createdAt;
}
