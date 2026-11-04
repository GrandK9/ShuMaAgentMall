package com.shumamall.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品聚合服务：商品 CRUD / 分类 / 品牌 / SKU（MySQL）+ 评论区（MongoDB）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.product",
        "com.shumamall.comment",
        "com.shumamall.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall.comment.feign")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
