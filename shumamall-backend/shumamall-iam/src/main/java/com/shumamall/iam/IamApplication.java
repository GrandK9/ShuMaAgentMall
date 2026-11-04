package com.shumamall.iam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 身份与访问合并进程：认证、用户账户、RBAC 与审计。
 */
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.auth",
        "com.shumamall.user",
        "com.shumamall.permission",
        "com.shumamall.common"
})
@MapperScan(basePackages = {
        "com.shumamall.auth.dao",
        "com.shumamall.user.dao",
        "com.shumamall.permission.dao"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall")
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
