package com.shumamall.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体（perm_role）。
 */
@Data
@TableName("perm_role")
public class RoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称，如 "运营编辑" */
    private String name;

    /** 角色编码，如 "role_editor"，全局唯一 */
    private String code;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
