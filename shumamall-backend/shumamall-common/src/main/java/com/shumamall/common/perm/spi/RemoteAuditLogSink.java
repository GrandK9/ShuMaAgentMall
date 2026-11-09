package com.shumamall.common.perm.spi;

import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.perm.audit.AuditLogReporter;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/**
 * HTTP 异步上报审计至 IAM（非 IAM 进程使用）。
 */
public class RemoteAuditLogSink implements AuditLogSink {

    private final AuditLogReporter delegate;

    public RemoteAuditLogSink(DiscoveryClient discoveryClient) {
        this.delegate = new AuditLogReporter(discoveryClient);
    }

    @Override
    public void report(AuditLogDTO dto) {
        delegate.report(dto);
    }
}
