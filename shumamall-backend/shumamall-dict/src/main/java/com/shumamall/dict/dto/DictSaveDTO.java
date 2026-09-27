package com.shumamall.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项创建/修改请求 DTO（管理端）。
 */
@Data
public class DictSaveDTO {

    /** 字典类型编码，如 product_spec */
    @NotBlank(message = "字典类型不能为空")
    private String type;

    /** 字典类型中文名，如 "商品规格" */
    @NotBlank(message = "字典类型名称不能为空")
    private String typeLabel;

    /** 字典项编码，如 color_black */
    @NotBlank(message = "字典项编码不能为空")
    private String code;

    /** 字典项显示值，如 "黑色" */
    @NotBlank(message = "字典项名称不能为空")
    private String label;

    /** 字典项实际值（可选） */
    private String value;

    /** 父级编码（可选，构建树形结构） */
    private String parentCode;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
