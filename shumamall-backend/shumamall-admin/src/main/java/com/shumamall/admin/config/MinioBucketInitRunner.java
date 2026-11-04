package com.shumamall.admin.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时确保 MinIO 桶存在；公开桶配置匿名读策略供商品主图直链访问。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitRunner implements ApplicationRunner {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public void run(ApplicationArguments args) {
        ensureBucket(minioConfig.getBucket(), false);
        if (StringUtils.hasText(minioConfig.getPublicBucket())) {
            ensureBucket(minioConfig.getPublicBucket(), true);
        }
    }

    private void ensureBucket(String bucket, boolean publicRead) {
        if (!StringUtils.hasText(bucket)) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 已创建: {}", bucket);
            }
            if (publicRead) {
                applyPublicReadPolicy(bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败: bucket={}, error={}", bucket, e.getMessage());
        }
    }

    private void applyPublicReadPolicy(String bucket) throws Exception {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        log.info("MinIO bucket 已配置匿名读: {}", bucket);
    }
}
