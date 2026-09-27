package com.shumamall.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类 DTO（请求/响应通用）。
 */
@Data
public class CategoryDTO {

    private Long id;

    /** 父分类ID, 0为顶级 */
    private Long parentId;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    private String name;

    /** 图标URL */
    private String icon;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态 0-隐藏 1-显示 */
    private Integer status;

    /** 子分类列表 */
    private List<CategoryDTO> children = new ArrayList<>();
}
