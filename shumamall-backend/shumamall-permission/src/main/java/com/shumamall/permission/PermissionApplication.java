package com.shumamall.permission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 权限服务启动类。
 * <p>
 * 端口 8086，提供 RBAC 权限管理、菜单树和权限校验能力。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall")
public class PermissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PermissionApplication.class, args);
    }
}
