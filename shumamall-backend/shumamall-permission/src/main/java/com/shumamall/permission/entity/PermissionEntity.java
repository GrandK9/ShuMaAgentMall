package com.shumamall.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点实体（perm_permission）。
 * <p>
 * 定义了系统中的功能权限，包括菜单、按钮和接口三种类型。
 */
@Data
@TableName("perm_permission")
public class PermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码，如 "product:edit"，全局唯一 */
    private String code;

    /** 权限中文名，如 "商品编辑" */
    private String name;

    /** 类型：0-MENU（菜单）1-BUTTON（按钮）2-API（接口） */
    private Integer type;

    /** 前端路由前缀，如 "/admin/product/**" */
    private String pathPrefix;

    /** 上级权限 ID，0 表示根节点 */
    private Long parentId;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
