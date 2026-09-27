package com.shumamall.permission.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志视图对象（返回管理端直接渲染）。
 */
@Data
public class AuditLogVO {

    /** 审计记录 ID（19 位雪花 ID，超过 JS 安全整数，必须按字符串下发） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 操作人用户 ID */
    private Long adminUserId;

    /** 权限编码，多个用逗号连接 */
    private String action;

    /** 请求 URI */
    private String resource;

    /** HTTP 方法 */
    private String method;

    /** 入参 JSON */
    private String params;

    /** 结果：SUCCESS / FORBIDDEN / FAILED */
    private String result;

    /** 客户端 IP */
    private String ip;

    /** 操作时间（前端直接展示的格式） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime occurredAt;

    /** 调用耗时（毫秒） */
    private Long costMs;
}
