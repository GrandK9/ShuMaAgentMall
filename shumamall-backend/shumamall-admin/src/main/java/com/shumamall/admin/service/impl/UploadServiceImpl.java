package com.shumamall.admin.service.impl;

import com.shumamall.admin.config.MinioConfig;
import com.shumamall.admin.dto.UploadVO;
import com.shumamall.admin.service.UploadService;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传：通用文件走私有桶 + 短时签名；商品主图走公开桶 + 稳定直链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final Set<String> PRODUCT_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public UploadVO upload(MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        String objectName = generateObjectName("misc/", extension);
        putObject(minioConfig.getBucket(), objectName, file);

        String presignedUrl = presignGet(minioConfig.getBucket(), objectName);
        return new UploadVO(presignedUrl, objectName, file.getSize());
    }

    @Override
    public UploadVO uploadProductImage(MultipartFile file) {
        validateProductImage(file);
        String bucket = minioConfig.getPublicBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "未配置商品图片存储桶");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String objectName = generateObjectName("product/", extension);
        putObject(bucket, objectName, file);

        String publicUrl = buildPublicObjectUrl(bucket, objectName);
        log.info("Product image uploaded: bucket={}, object={}, url={}", bucket, objectName, publicUrl);
        return new UploadVO(publicUrl, objectName, file.getSize());
    }

    private void validateProductImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        long max = minioConfig.getProductImageMaxBytes();
        if (max > 0 && file.getSize() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "图片大小不能超过 " + (max / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !PRODUCT_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 JPG、PNG、WebP、GIF 图片");
        }
    }

    private void putObject(String bucket, String objectName, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload to MinIO: bucket={}, object={}", bucket, objectName, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    private String presignGet(String bucket, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "生成访问链接失败");
        }
    }

    private String buildPublicObjectUrl(String bucket, String objectName) {
        String base = minioConfig.getPublicEndpoint();
        if (!StringUtils.hasText(base)) {
            base = minioConfig.getEndpoint();
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/" + bucket + "/" + objectName;
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return ".jpg";
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (ext.length() > 8) {
            return ".jpg";
        }
        return ext;
    }

    private String generateObjectName(String prefix, String extension) {
        return prefix + UUID.randomUUID().toString().replace("-", "") + extension;
    }
}
