package com.shumamall.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shumamall.common.crypto.EncryptTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（auth 模块直接操作用户表进行认证）。
 */
@Data
@TableName(value = "user", autoResultMap = true)
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    /** 手机号（AES-256-CBC + 随机 IV 加密存储） */
    @TableField(value = "phone", typeHandler = EncryptTypeHandler.class)
    private String phone;

    /** 邮箱（AES-256-CBC + 随机 IV 加密存储） */
    @TableField(value = "email", typeHandler = EncryptTypeHandler.class)
    private String email;

    private String avatar;

    private String nickname;

    /** 角色 user-普通用户 admin-管理员 */
    private String role;

    /** 性别 0-未知 1-男 2-女 */
    private Integer gender;

    /** 状态 0-禁用 1-正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
