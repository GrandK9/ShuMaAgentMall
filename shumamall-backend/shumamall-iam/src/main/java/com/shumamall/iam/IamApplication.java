package com.shumamall.iam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.shumamall.admin.feign.OrderFeignClient;
import com.shumamall.admin.feign.ProductFeignClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理域合并进程：认证、用户、RBAC、仪表盘/上传、系统字典（auth + user + permission + admin + dict 模块）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.auth",
        "com.shumamall.user",
        "com.shumamall.permission",
        "com.shumamall.admin",
        "com.shumamall.dict",
        "com.shumamall.common"
})
@MapperScan(basePackages = {
        "com.shumamall.auth.dao",
        "com.shumamall.user.dao",
        "com.shumamall.permission.dao",
        "com.shumamall.dict.dao"
})
@EnableDiscoveryClient
@EnableFeignClients(clients = {OrderFeignClient.class, ProductFeignClient.class})
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
