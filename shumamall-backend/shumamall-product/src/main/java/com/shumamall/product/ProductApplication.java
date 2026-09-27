package com.shumamall.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务启动类。
 * <p>
 * 端口 8083，提供商品 CRUD、分类、品牌、SKU 管理。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
