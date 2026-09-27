package com.shumamall.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 权限点 DTO。
 */
@Data
public class PermissionDTO {

    private Long id;

    /** 权限编码，如 "product:edit" */
    @NotBlank(message = "权限编码不能为空")
    private String code;

    /** 权限中文名，如 "商品编辑" */
    @NotBlank(message = "权限名称不能为空")
    private String name;

    /** 类型：0-MENU 1-BUTTON 2-API */
    @NotNull(message = "权限类型不能为空")
    private Integer type;

    /** 前端路由前缀 */
    private String pathPrefix;

    /** 上级权限 ID，0 表示根节点 */
    private Long parentId;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态：0-禁用 1-启用 */
    private Integer status;
}
