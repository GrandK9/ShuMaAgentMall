package com.shumamall.permission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 管理员-角色关联实体（perm_admin_user_role）。
 * <p>
 * 多对多中间表，关联主库的 admin_user 表与权限库的角色表。
 */
@Data
@TableName("perm_admin_user_role")
public class AdminUserRoleEntity {

    /** 管理员用户 ID（关联主库 admin_user.id） */
    private Long adminUserId;

    /** 角色 ID */
    private Long roleId;
}
