package com.shumamall.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理端聚合服务：仪表盘、上传、系统码表（dict 模块）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.admin",
        "com.shumamall.dict",
        "com.shumamall.common"
})
@MapperScan("com.shumamall.dict.dao")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall.admin")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
