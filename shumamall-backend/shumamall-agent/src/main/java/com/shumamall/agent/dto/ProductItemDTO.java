package com.shumamall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品摘要 DTO（Feign 调用 product 服务后精简字段，供工具返回 / LLM 汇总）。
 */
@Data
public class ProductItemDTO {

    private Long id;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 分类名称（服务端回填，供检索/展示用） */
    private String categoryName;

    /** 品牌名称（服务端回填，供检索/展示用） */
    private String brandName;

    /** 商品详情(HTML)，供建立全文索引 */
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

    /** SKU 列表（详情接口返回，下单/加购时用于解析默认 SKU） */
    private List<ProductSkuDTO> skuList;
}
