package com.shumamall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动类。
 * <p>
 * 端口 8081，提供注册/登录接口，签发 JWT token。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
