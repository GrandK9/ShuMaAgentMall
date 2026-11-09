package com.shumamall.common.perm.config;

import com.shumamall.common.perm.aspect.PermissionAspect;
import com.shumamall.common.perm.spi.AuditLogSink;
import com.shumamall.common.perm.spi.RemoteAuditLogSink;
import com.shumamall.common.perm.spi.RemoteUserPermissionsLookup;
import com.shumamall.common.perm.spi.UserPermissionsLookup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnClass(RestTemplate.class)
public class PermissionAspectConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(UserPermissionsLookup.class)
    @ConditionalOnClass(DiscoveryClient.class)
    public UserPermissionsLookup remoteUserPermissionsLookup(DiscoveryClient discoveryClient,
                                                             RestTemplate restTemplate) {
        return new RemoteUserPermissionsLookup(discoveryClient, restTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogSink.class)
    @ConditionalOnClass(DiscoveryClient.class)
    public AuditLogSink remoteAuditLogSink(DiscoveryClient discoveryClient) {
        return new RemoteAuditLogSink(discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({UserPermissionsLookup.class, AuditLogSink.class})
    public PermissionAspect permissionAspect(UserPermissionsLookup userPermissionsLookup,
                                             AuditLogSink auditLogSink) {
        return new PermissionAspect(userPermissionsLookup, auditLogSink);
    }
}
