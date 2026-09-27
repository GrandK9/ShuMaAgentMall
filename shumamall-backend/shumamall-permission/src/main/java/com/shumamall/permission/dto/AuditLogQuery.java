package com.shumamall.permission.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询参数。
 * <p>
 * 所有筛选条件均可为空，为空表示不参与过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQuery extends PageQuery {

    /** 操作人用户 ID，精确匹配 */
    private Long adminUserId;

    /** 权限编码，模糊匹配 */
    private String action;

    /** 结果，精确匹配：SUCCESS / FORBIDDEN / FAILED */
    private String result;

    /** 请求 URI，模糊匹配 */
    private String resource;

    /** 起始时间（含），ISO-8601，如 2026-09-17T00:00:00 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含），ISO-8601 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
