package com.shumamall.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 评论区服务启动类。
 * <p>
 * 评论数据存储于 MongoDB（文档模型，天然支持嵌套回复），
 * 点赞计数防重使用 Redis。
 * <p>
 * {@code scanBasePackages = "com.shumamall"}：本服务的 WebMvcConfig 需要注入
 * {@code com.shumamall.common.auth.JwtUtils}，只扫 {@code com.shumamall.comment}
 * 会导致 "No qualifying bean of type JwtUtils" 启动失败；与其余微服务保持一致。
 * <p>
 * {@code exclude = DataSourceAutoConfiguration.class}：扫描公共模块会带上 MyBatis-Plus 自动配置，
 * 而本服务只用 MongoDB 不持有关系库，不排除会报 "Failed to configure a DataSource"。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall",
        exclude = DataSourceAutoConfiguration.class)
@EnableFeignClients
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }
}
