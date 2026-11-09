package com.shumamall.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端操作审计（MySQL 表 audit_log）。
 */
@Data
@TableName("audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long adminUserId;

    private String action;

    private String resource;

    private String method;

    private String params;

    private String result;

    private String ip;

    private LocalDateTime occurredAt;

    private Long costMs;
}
