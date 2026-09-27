package com.shumamall.admin.service.impl;

import com.shumamall.admin.dto.UploadVO;
import com.shumamall.admin.service.UploadService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传服务实现。
 * <p>
 * 将文件上传至 MinIO，返回 1 小时有效的签名访问 URL。
 *
 * @author ShuMaMall Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 上传文件到 MinIO，生成唯一文件名并返回签名 URL。
     *
     * @param file 待上传文件
     * @return 上传结果视图对象
     */
    @Override
    public UploadVO upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String objectName = generateObjectName(extension);

        try (InputStream inputStream = file.getInputStream()) {
            // 上传文件到 MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("File uploaded to MinIO: bucket={}, object={}, size={}", bucket, objectName, file.getSize());

            // 生成 1 小时有效的签名 URL
            String presignedUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());

            log.info("Presigned URL generated for object: {}", objectName);

            return new UploadVO(presignedUrl, objectName, file.getSize());

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", originalFilename, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文件名中提取扩展名（含点号）。
     *
     * @param filename 原始文件名
     * @return 扩展名，如 ".jpg"，若无扩展名则返回空字符串
     */
    private String extractExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    /**
     * 生成唯一对象名（UUID + 扩展名）。
     *
     * @param extension 文件扩展名
     * @return 唯一对象名
     */
    private String generateObjectName(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }
}
