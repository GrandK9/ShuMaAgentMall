package com.shumamall.video.config;

import io.minio.MinioClient;
import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储配置。
 * <p>
 * 从 bootstrap.yml / Nacos 配置读取连接参数，创建 {@link MinioClient} Bean。
 * 三个 bucket（raw / hls / thumb）在 {@link MinioBucketInitRunner} 中初始化。
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

    /** 原始视频存储桶 */
    private String rawBucket = "shumamall-raw";

    /** HLS 切片存储桶 */
    private String hlsBucket = "video-hls";

    /** 缩略图存储桶 */
    private String thumbBucket = "video-thumb";

    /**
     * 创建 MinIO 客户端 Bean。
     *
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient(VideoProperties videoProperties) {
        VideoProperties.Io io = videoProperties.getIo();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(io.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(io.getReadIdleTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(io.getWriteIdleTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }
}
