package com.shumamall.permission.config;

import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.perm.spi.AuditLogSink;
import com.shumamall.common.perm.spi.UserPermissionsLookup;
import com.shumamall.permission.service.AuditLogService;
import com.shumamall.permission.service.PermissionCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IAM 合并部署：权限校验与审计走本地 Bean，避免对本进程 HTTP 回环。
 */
@Configuration
public class LocalPermissionInfrastructureConfig {

    @Bean
    public UserPermissionsLookup userPermissionsLookup(PermissionCacheService permissionCacheService) {
        return userId -> {
            Set<String> cached = permissionCacheService.getCachedPermissions(userId);
            return cached != null ? cached : Set.of();
        };
    }

    @Bean
    public AuditLogSink auditLogSink(AuditLogService auditLogService) {
        return new LocalAuditLogSink(auditLogService);
    }

    @Slf4j
    static final class LocalAuditLogSink implements AuditLogSink {

        private static final int QUEUE_CAPACITY = 1000;

        private final AuditLogService auditLogService;
        private final ExecutorService executor;
        private final AtomicLong droppedCount = new AtomicLong();

        LocalAuditLogSink(AuditLogService auditLogService) {
            this.auditLogService = auditLogService;
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable, "audit-local");
                        thread.setDaemon(true);
                        return thread;
                    });
            this.executor = pool;
        }

        @Override
        public void report(AuditLogDTO dto) {
            if (dto == null) {
                return;
            }
            try {
                executor.execute(() -> {
                    try {
                        auditLogService.save(dto);
                    } catch (Exception e) {
                        log.warn("本地审计落库失败（不影响业务）: action={}, cause={}",
                                dto.getAction(), e.getMessage());
                    }
                });
            } catch (RejectedExecutionException e) {
                long dropped = droppedCount.incrementAndGet();
                log.warn("本地审计队列已满，丢弃 action={}, 累计={}", dto.getAction(), dropped);
            }
        }
    }
}
