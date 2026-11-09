package com.shumamall.common.perm.spi;

import com.shumamall.common.perm.audit.AuditLogDTO;

/**
 * 审计记录投递（供 {@code PermissionAspect} 使用）。
 * <p>
 * IAM 进程内直写 MongoDB；远端服务 HTTP 上报至 IAM。
 */
public interface AuditLogSink {

    /**
     * 异步、尽力而为地上报审计，不得抛异常阻断业务。
     */
    void report(AuditLogDTO dto);
}
