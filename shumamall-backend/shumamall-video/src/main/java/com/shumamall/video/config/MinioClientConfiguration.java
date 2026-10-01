package com.shumamall.video.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端 Bean：与 {@link MinioConfig} 分离，保证配置属性绑定完成后再创建客户端。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioClientConfiguration {

    private final MinioConfig minioConfig;
    private final VideoProperties videoProperties;

    @Bean
    @Primary
    public MinioClient minioClient() {
        log.info("MinIO 内部客户端 endpoint={}", minioConfig.getEndpoint());
        return minioConfig.buildClient(minioConfig.getEndpoint(), videoProperties);
    }

    @Bean("minioPresignClient")
    public MinioClient minioPresignClient() {
        String presignEndpoint = StringUtils.hasText(minioConfig.getPublicEndpoint())
                ? minioConfig.getPublicEndpoint()
                : minioConfig.getEndpoint();
        log.info("MinIO 预签名客户端 endpoint={} (public-endpoint={})",
                presignEndpoint, minioConfig.getPublicEndpoint());
        return minioConfig.buildPresignClient(presignEndpoint, minioConfig.getEndpoint(), videoProperties);
    }
}
