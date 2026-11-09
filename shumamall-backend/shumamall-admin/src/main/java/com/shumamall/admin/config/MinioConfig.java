package com.shumamall.admin.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置。
 * <p>
 * 从 Spring 配置（IAM bootstrap / 环境变量）读取 MinIO 连接参数，创建 MinioClient Bean。
 *
 * @author ShuMaMall Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO 服务端点，如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 默认存储桶名（通用上传，签名 URL） */
    private String bucket;

    /** 浏览器可访问的 MinIO 根地址，用于拼接公开对象直链 */
    private String publicEndpoint;

    /** 商品主图等公开可读资源桶 */
    private String publicBucket;

    /** 商品主图单文件大小上限（字节） */
    private long productImageMaxBytes = 5 * 1024 * 1024;

    /**
     * 创建 MinIO 客户端 Bean。
     *
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
