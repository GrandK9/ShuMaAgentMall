package com.shumamall.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品 SKU DTO。
 */
@Data
@NoArgsConstructor
public class ProductSkuDTO {

    private Long id;

    /** 商品ID */
    private Long productId;

    /** SKU编码 */
    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;

    /** 规格值 JSON */
    private String specs;

    /** SKU价格 */
    @NotNull(message = "SKU价格不能为空")
    @DecimalMin(value = "0.01", message = "SKU价格必须大于0")
    private BigDecimal price;

    /** SKU库存 */
    @Min(value = 0, message = "SKU库存不能为负")
    private Integer stock;

    /** SKU图片 */
    private String image;

    /** 状态 0-禁用 1-启用 */
    private Integer status;
}
