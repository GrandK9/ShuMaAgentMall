package com.shumamall.common.perm.audit;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端操作审计记录（跨服务传输对象）。
 * <p>
 * 由 {@link AuditLogReporter} 从**业务服务**（product / order / payment / video 等）
 * 上报到 shumamall-permission 服务，由其落库到 MongoDB（集合 {@code audit_log}）。
 * 与 {@code product_comment} 等文档模型不同，审计日志是「业务服务的写入方」与
 * 「存储方」分离的，因此这里需要一个独立的传输对象。
 * <p>
 * {@code occurredAtEpochMilli} 用 epoch 毫秒而非 {@code java.time} 类型：
 * 这条链路走的是裸 {@code RestTemplate} 自带的 Jackson 转换器，而不是 Spring Boot
 * 自动配置的 {@code ObjectMapper}，是否注册 JavaTimeModule 取决于转换器的内部构造，
 * 传 long 则完全不依赖序列化配置。落库时再转成 {@code LocalDateTime}。
 */
@Data
public class AuditLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作人用户 ID（来自 JWT，见 SecurityContext） */
    private Long adminUserId;

    /** 权限编码，多个用逗号连接，如 "product:edit,product:create" */
    private String action;

    /** 请求 URI，如 /api/v1/admin/product/save */
    private String resource;

    /** HTTP 方法，如 POST / PUT / DELETE */
    private String method;

    /** 入参 JSON（已剔除文件流等不可序列化参数，并截断长度） */
    private String params;

    /** 校验/执行结果，取值见 {@code AuditResult}：SUCCESS / FORBIDDEN / FAILED */
    private String result;

    /** 客户端 IP（优先取 X-Forwarded-For 首个地址，否则 remoteAddr） */
    private String ip;

    /** 操作发生时刻（epoch 毫秒），由业务服务侧打点，而非落库时补写 */
    private Long occurredAtEpochMilli;

    /** 本次调用耗时（毫秒）：FORBIDDEN 时为权限校验耗时，SUCCESS/FAILED 时为整个方法耗时 */
    private Long costMs;
}
