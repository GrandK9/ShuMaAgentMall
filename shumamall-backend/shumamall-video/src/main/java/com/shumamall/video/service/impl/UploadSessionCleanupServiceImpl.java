package com.shumamall.video.service.impl;

import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.service.UploadSessionCleanupService;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 合并完成后删除 raw 分片并移除上传会话；失败标记 {@code cleanup_failed} 供定时重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSessionCleanupServiceImpl implements UploadSessionCleanupService {

    private static final String CHUNK_NAME_FRAGMENT = "/chunk_";
    private static final long RETRY_DELAY_MS = 500L;

    private final MongoTemplate mongoTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public void finalizeSessionAfterMerge(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        if (attemptPurgeAndRemoveSession(sessionId, 2)) {
            log.info("上传会话收尾完成: sessionId={}", sessionId);
            return;
        }
        markCleanupFailed(sessionId, "分片清理未完成，将自动重试");
    }

    @Override
    public void retryPendingSessionCleanups() {
        retryCleanupFailedSessions();
        cleanupAbandonedUploads();
    }

    private void retryCleanupFailedSessions() {
        List<UploadSessionDoc> pending = mongoTemplate.find(
                new Query(Criteria.where("status").is(VideoConstants.SESSION_CLEANUP_FAILED)),
                UploadSessionDoc.class);
        for (UploadSessionDoc session : pending) {
            String sessionId = session.getSessionId();
            try {
                if (attemptPurgeAndRemoveSession(sessionId, 1)) {
                    log.info("定时重试清理成功: sessionId={}", sessionId);
                }
            } catch (Exception e) {
                log.warn("定时重试清理失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 过期仍未 complete 的上传：删除目录内无 metadata 引用的对象并移除会话文档。
     */
    private void cleanupAbandonedUploads() {
        List<UploadSessionDoc> abandoned = mongoTemplate.find(
                new Query(Criteria.where("status").is(VideoConstants.SESSION_UPLOADING)
                        .and("expireAt").lt(LocalDateTime.now())),
                UploadSessionDoc.class);
        for (UploadSessionDoc session : abandoned) {
            String sessionId = session.getSessionId();
            try {
                purgeUnreferencedObjectsUnderSession(sessionId);
                mongoTemplate.remove(session);
                log.info("已清理过期未完成上传: sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("清理过期上传失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 删除分片、校验无 chunk 残留、移除会话文档。
     *
     * @param maxAttempts 含首次在内的尝试次数
     */
    private boolean attemptPurgeAndRemoveSession(String sessionId, int maxAttempts) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                sleepQuietly(RETRY_DELAY_MS);
            }
            if (session != null && session.getChunks() != null) {
                deleteChunkIndexes(sessionId, session.getChunks());
            }
            deleteRemainingChunkObjectsByListing(sessionId);
            if (!hasRemainingChunks(sessionId)) {
                if (session != null) {
                    mongoTemplate.remove(session);
                } else {
                    mongoTemplate.remove(new Query(Criteria.where("_id").is(sessionId)), UploadSessionDoc.class);
                }
                return true;
            }
        }
        return false;
    }

    private void purgeUnreferencedObjectsUnderSession(String sessionId) throws Exception {
        String prefix = rawPrefix(sessionId);
        List<String> objects = listObjectsUnderPrefix(prefix);
        if (objects.isEmpty()) {
            return;
        }
        Set<String> referenced = findReferencedRawObjects(objects);
        for (String object : objects) {
            if (!referenced.contains(object)) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getRawBucket())
                        .object(object)
                        .build());
            }
        }
    }

    private Set<String> findReferencedRawObjects(List<String> objects) {
        Set<String> referenced = new HashSet<>();
        List<VideoMetaDoc> metas = mongoTemplate.find(
                new Query(Criteria.where("rawObject").in(objects)), VideoMetaDoc.class);
        for (VideoMetaDoc meta : metas) {
            if (StringUtils.hasText(meta.getRawObject())) {
                referenced.add(meta.getRawObject());
            }
        }
        return referenced;
    }

    private void deleteChunkIndexes(String sessionId, List<Integer> chunkIndexes) {
        for (Integer index : chunkIndexes) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getRawBucket())
                        .object(chunkObject(sessionId, index))
                        .build());
            } catch (Exception e) {
                log.warn("删除分片失败: sessionId={}, chunk={}, error={}",
                        sessionId, index, e.getMessage());
            }
        }
    }

    private void deleteRemainingChunkObjectsByListing(String sessionId) {
        try {
            for (String object : listObjectsUnderPrefix(rawPrefix(sessionId))) {
                if (object.contains(CHUNK_NAME_FRAGMENT)) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioConfig.getRawBucket())
                            .object(object)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("列举并删除分片失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private boolean hasRemainingChunks(String sessionId) {
        try {
            for (String object : listObjectsUnderPrefix(rawPrefix(sessionId))) {
                if (object.contains(CHUNK_NAME_FRAGMENT)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("校验分片残留失败: sessionId={}, error={}", sessionId, e.getMessage());
            return true;
        }
    }

    private List<String> listObjectsUnderPrefix(String prefix) throws Exception {
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
        return objects;
    }

    private void markCleanupFailed(String sessionId, String reason) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session == null) {
            return;
        }
        session.setStatus(VideoConstants.SESSION_CLEANUP_FAILED);
        session.setFailReason(reason);
        mongoTemplate.save(session);
        log.warn("上传会话标记 cleanup_failed: sessionId={}", sessionId);
    }

    private static String rawPrefix(String sessionId) {
        return "raw/" + sessionId + "/";
    }

    private static String chunkObject(String sessionId, Integer chunkIndex) {
        return "raw/" + sessionId + "/chunk_" + chunkIndex;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
