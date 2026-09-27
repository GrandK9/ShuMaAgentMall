package com.shumamall.permission.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 管理端操作审计文档（MongoDB 集合：audit_log）。
 * <p>
 * 由各业务服务的 {@code com.shumamall.common.perm.aspect.PermissionAspect} 环绕
 * {@code @RequirePermission} 方法时上报，本服务落库。
 * 用 MongoDB 而非 MySQL 的原因：审计是「写多读少、字段随业务演进、无事务需求」的典型场景，
 * 且不该与权限表（shumamall_perm 库）争抢连接和锁。
 */
@Data
@Document(collection = "audit_log")
@CompoundIndexes({
        // 最常用的查询组合：某操作人的最近操作、按结果筛选越权尝试
        @CompoundIndex(name = "idx_user_occurred", def = "{'adminUserId': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "idx_result_occurred", def = "{'result': 1, 'occurredAt': -1}")
})
public class AuditLogDocument {

    /** 审计记录 ID（雪花 ID，与全项目主键生成策略一致） */
    @Id
    private Long id;

    /** 操作人用户 ID（来自 JWT 的 user_id） */
    private Long adminUserId;

    /** 权限编码，多个用逗号连接 */
    private String action;

    /** 请求 URI */
    private String resource;

    /** HTTP 方法 */
    private String method;

    /** 入参 JSON（已截断） */
    private String params;

    /** 结果：SUCCESS / FORBIDDEN / FAILED */
    private String result;

    /** 客户端 IP */
    private String ip;

    /** 操作发生时刻（业务服务侧打点），倒序分页的主排序字段 */
    @Indexed
    private LocalDateTime occurredAt;

    /** 调用耗时（毫秒） */
    private Long costMs;
}
