package com.shumamall.common.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 信息 DTO（内部 Feign 调用，含商品名称/图片/规格/价格）。
 */
@Data
public class SkuInfoDTO {

    /** SKU ID */
    private Long skuId;

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品主图 */
    private String productImage;

    /** SKU 规格 JSON */
    private String skuSpecs;

    /** SKU 价格 */
    private BigDecimal price;

    /** SKU 库存 */
    private Integer stock;
}
