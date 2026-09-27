package com.shumamall.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 品牌 DTO（请求/响应通用）。
 */
@Data
public class BrandDTO {

    private Long id;

    /** 品牌名称 */
    @NotBlank(message = "品牌名称不能为空")
    private String name;

    /** 品牌Logo */
    private String logo;

    /** 品牌描述 */
    private String description;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态 0-隐藏 1-显示 */
    private Integer status;
}
