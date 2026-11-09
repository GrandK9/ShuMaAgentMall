package com.shumamall.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品聚合服务：商品 CRUD / 分类 / 品牌 / SKU + 评论区（MySQL）。
 * <p>
 * Mapper 接口在启动类子包内并标注 {@code @Mapper}，由 MyBatis 自动配置扫描，无需 {@code @MapperScan}。
 */
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.product",
        "com.shumamall.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shumamall.product.comment.feign")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
