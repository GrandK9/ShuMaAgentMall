package com.shumamall.common.perm.config;

import com.shumamall.common.perm.aspect.PermissionAspect;
import com.shumamall.common.perm.audit.AuditLogReporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 权限校验 AOP 自动配置。
 * <p>
 * 仅在 classpath 中同时存在 RestTemplate 和 DiscoveryClient（即启用了 Spring Web + 服务发现）时生效，
 * 避免在网关（WebFlux）等不含 spring-web 的模块中加载报错。
 */
@Configuration
@ConditionalOnClass({RestTemplate.class, DiscoveryClient.class})
public class PermissionAspectConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 审计上报器。线程池在首次上报时才真正创建线程，无 {@code @RequirePermission} 调用的服务零开销。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogReporter auditLogReporter(DiscoveryClient discoveryClient) {
        return new AuditLogReporter(discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect(DiscoveryClient discoveryClient, RestTemplate restTemplate,
                                             AuditLogReporter auditLogReporter) {
        return new PermissionAspect(discoveryClient, restTemplate, auditLogReporter);
    }
}
