package com.shumamall.video.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * MinIO bucket 初始化器。
 * <p>
 * 服务启动时确保 raw / hls / thumb 三个 bucket 存在（不存在则创建），
 * 避免首次上传因 bucket 缺失失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitRunner implements ApplicationRunner {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public void run(ApplicationArguments args) {
        ensureBucket(minioConfig.getRawBucket());
        ensureBucket(minioConfig.getHlsBucket());
        ensureBucket(minioConfig.getThumbBucket());
    }

    /**
     * 确保指定 bucket 存在。
     *
     * @param bucket bucket 名称
     */
    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 已创建: {}", bucket);
            } else {
                log.debug("MinIO bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败: bucket={}, error={}", bucket, e.getMessage());
        }
    }
}
