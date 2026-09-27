package com.shumamall.permission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联实体（perm_role_permission）。
 * <p>
 * 多对多中间表，仅用于插入和查询，无自增主键。
 */
@Data
@TableName("perm_role_permission")
public class RolePermissionEntity {

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;
}
