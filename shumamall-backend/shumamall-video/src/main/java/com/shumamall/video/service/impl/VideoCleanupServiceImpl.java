package com.shumamall.video.service.impl;

import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.service.VideoCleanupService;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 视频残留清理服务实现。
 * <p>
 * 每 6 小时扫描 MinIO raw bucket 的 {@code raw/{sessionId}/} 目录（跳过未过期的
 * {@code uploading} 会话，避免误删进行中的分片）：
 * 删除未被 {@code video_metadata.rawObject} 引用的对象（合并失败残留的分片、
 * 过期未完成上传的半成品）；被引用的 merged.mp4 / source.mp4 保留（重转码源）。
 * 若对应上传会话文档已过期（TTL 兜底），一并主动删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCleanupServiceImpl implements VideoCleanupService {

    private final MongoTemplate mongoTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    @Scheduled(fixedDelay = VideoConstants.CLEANUP_INTERVAL_MS)
    public void cleanupExpiredSessions() {
        List<String> prefixes;
        try {
            prefixes = listSessionPrefixes();
        } catch (Exception e) {
            log.error("列举 MinIO raw 目录失败，跳过本轮清理", e);
            return;
        }
        for (String prefix : prefixes) {
            try {
                cleanupPrefix(prefix);
            } catch (Exception e) {
                // 单目录清理失败不影响其他目录
                log.warn("清理 MinIO raw 目录失败（忽略）: prefix={}, error={}", prefix, e.getMessage());
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 列举 raw bucket 下所有一级目录（{@code raw/{sessionId}/}），
     * 不递归、不拉取对象明细，开销可控。
     *
     * @return 目录前缀列表
     * @throws Exception MinIO 列举失败
     */
    private List<String> listSessionPrefixes() throws Exception {
        List<String> prefixes = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(minioConfig.getRawBucket()).prefix("raw/").build());
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.isDir()) {
                prefixes.add(item.objectName());
            }
        }
        return prefixes;
    }

    /**
     * 清理单个 raw 目录。
     *
     * @param prefix 目录前缀，形如 {@code raw/{sessionId}/}
     * @throws Exception MinIO 操作失败
     */
    private void cleanupPrefix(String prefix) throws Exception {
        String sessionId = extractSessionId(prefix);
        if (shouldSkipActiveUpload(sessionId)) {
            log.debug("跳过进行中的上传目录: prefix={}", prefix);
            return;
        }

        // 1. 列出该目录下全部对象
        List<String> objects = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(minioConfig.getRawBucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build());
        for (Result<Item> result : results) {
            objects.add(result.get().objectName());
        }
        if (objects.isEmpty()) {
            return;
        }

        // 2. 找出被 video_metadata.rawObject 引用的对象（重转码源，必须保留）
        Set<String> referenced = new HashSet<>();
        List<VideoMetaDoc> metas = mongoTemplate.find(
                new Query(Criteria.where("rawObject").in(objects)), VideoMetaDoc.class);
        for (VideoMetaDoc meta : metas) {
            if (StringUtils.hasText(meta.getRawObject())) {
                referenced.add(meta.getRawObject());
            }
        }

        // 3. 删除未被引用的残留对象（合并后分片、半成品等）
        int removed = 0;
        for (String object : objects) {
            if (referenced.contains(object)) {
                continue;
            }
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket()).object(object).build());
            removed++;
        }
        log.info("MinIO raw 残留清理: prefix={}, 删除 {} 个, 保留引用 {} 个",
                prefix, removed, referenced.size());

        // 4. 若对应上传会话文档已过期（TTL 兜底），主动删除
        if (StringUtils.hasText(sessionId)) {
            UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
            if (session != null && session.getExpireAt() != null
                    && session.getExpireAt().isBefore(LocalDateTime.now())) {
                mongoTemplate.remove(session);
                log.info("清理过期上传会话文档: sessionId={}, status={}",
                        sessionId, session.getStatus());
            }
        }
    }

    /**
     * 未过期的 uploading 会话仍可能在上传分片，整目录跳过清理。
     */
    private boolean shouldSkipActiveUpload(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session == null) {
            return false;
        }
        if (!VideoConstants.SESSION_UPLOADING.equals(session.getStatus())) {
            return false;
        }
        return session.getExpireAt() != null && session.getExpireAt().isAfter(LocalDateTime.now());
    }

    /**
     * 从目录前缀提取会话 ID。
     * <p>
     * 前缀形如 {@code raw/{sessionId}/}；直传目录同样是一级目录
     * {@code raw/{snowflakeId}/}，其下对象被元数据引用，此处仅用于
     * 定位会话文档（直传无会话文档，findById 返回 null，自然跳过）。
     *
     * @param prefix 目录前缀
     * @return 会话 ID，格式不合法时返回 null
     */
    private String extractSessionId(String prefix) {
        String stripped = prefix.startsWith("raw/") ? prefix.substring("raw/".length()) : prefix;
        String normalized = stripped.endsWith("/")
                ? stripped.substring(0, stripped.length() - 1) : stripped;
        if (normalized.isEmpty() || normalized.contains("/")) {
            return null;
        }
        return normalized;
    }
}
