package com.shumamall.common.perm.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 经服务发现调用 IAM 内部权限接口（非 IAM 进程使用）。
 */
@Slf4j
public class RemoteUserPermissionsLookup implements UserPermissionsLookup {

    private static final String IAM_SERVICE = "shumamall-iam";

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    public RemoteUserPermissionsLookup(DiscoveryClient discoveryClient, RestTemplate restTemplate) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = restTemplate;
    }

    @Override
    public Set<String> getPermissions(Long userId) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(IAM_SERVICE);
            if (instances == null || instances.isEmpty()) {
                log.warn("IAM 不可用，无法拉取用户权限");
                return Collections.emptySet();
            }
            ServiceInstance instance = instances.get(0);
            String url = "http://" + instance.getHost() + ":" + instance.getPort()
                    + "/api/permission/internal/getUserPermissions?userId=" + userId;

            @SuppressWarnings("unchecked")
            Set<String> permissions = restTemplate.getForObject(url, Set.class);
            return permissions != null ? permissions : Collections.emptySet();
        } catch (Exception e) {
            log.warn("调用 IAM 获取权限失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
