package com.shumamall.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类。
 * <p>
 * 端口 8082，提供用户 CRUD 和收货地址管理。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
