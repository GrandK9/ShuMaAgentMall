package com.shumamall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 SKU 摘要 DTO（Feign 调用 product 服务详情后接收 skuList，供下单/加购工具解析默认 SKU）。
 */
@Data
public class ProductSkuDTO {

    private Long id;

    /** 商品ID */
    private Long productId;

    /** SKU编码 */
    private String skuCode;

    /** 规格值 JSON，如 {"颜色":"黑色","存储":"256GB"} */
    private String specs;

    /** SKU价格 */
    private BigDecimal price;

    /** SKU库存 */
    private Integer stock;

    /** SKU图片 */
    private String image;

    /** 状态 0-禁用 1-启用 */
    private Integer status;
}
