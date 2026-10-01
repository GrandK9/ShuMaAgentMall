package com.shumamall.video.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dto.CompleteUploadRespDTO;
import com.shumamall.video.dto.InitUploadReqDTO;
import com.shumamall.video.dto.InitUploadRespDTO;
import com.shumamall.video.dto.UploadRespDTO;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.service.UploadSessionCleanupService;
import com.shumamall.video.service.VideoProcessService;
import com.shumamall.video.service.VideoUploadService;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 视频上传服务实现。
 * <p>
 * 分片上传：initUpload（MongoDB 会话，TTL 24h）→ uploadChunk（MinIO raw 分片）
 * → completeUpload（composeObject 合并 → 创建 meta → 清理分片与会话 → 异步转码）。
 * 单次直传：评论区小文件直接落 raw → 同步校验 + 切片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadServiceImpl implements VideoUploadService {

    private final MongoTemplate mongoTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final VideoProcessService videoProcessService;
    private final UploadSessionCleanupService uploadSessionCleanupService;

    @Override
    public InitUploadRespDTO initUpload(Long uploaderId, InitUploadReqDTO dto) {
        String limitType = resolveLimitType(dto.getUploaderType());
        validateSize(dto.getFileSize(), limitType);

        UploadSessionDoc session = new UploadSessionDoc();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setFileName(dto.getFileName());
        session.setFileSize(dto.getFileSize());
        session.setMimeType(dto.getMimeType());
        session.setHash(dto.getHash());
        session.setUploaderId(uploaderId);
        session.setUploaderType(dto.getUploaderType());
        session.setSourceType(StringUtils.hasText(dto.getSourceType())
                ? dto.getSourceType() : VideoConstants.SOURCE_BROWSER);
        session.setLimitType(limitType);
        session.setStatus(VideoConstants.SESSION_UPLOADING);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpireAt(LocalDateTime.now().plusHours(VideoConstants.SESSION_TTL_HOURS));
        mongoTemplate.insert(session);

        log.info("初始化分片上传会话: sessionId={}, fileName={}, size={}, limitType={}",
                session.getSessionId(), dto.getFileName(), dto.getFileSize(), limitType);
        return new InitUploadRespDTO(session.getSessionId(),
                VideoConstants.CHUNK_SIZE, session.getExpireAt());
    }

    @Override
    public void uploadChunk(String sessionId, Integer chunkIndex, MultipartFile data) {
        UploadSessionDoc session = requireSession(sessionId);
        if (!VideoConstants.SESSION_UPLOADING.equals(session.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "会话已处理完成，无法继续上传");
        }
        if (session.getChunks().contains(chunkIndex)) {
            log.debug("分片已存在，跳过重复上传: sessionId={}, chunk={}", sessionId, chunkIndex);
            return;
        }
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket())
                    .object(chunkObject(sessionId, chunkIndex))
                    .stream(data.getInputStream(), data.getSize(), -1)
                    .contentType(data.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("分片上传 MinIO 失败: sessionId={}, chunk={}", sessionId, chunkIndex, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片上传失败");
        }
        session.getChunks().add(chunkIndex);
        mongoTemplate.save(session);
    }

    @Override
    public CompleteUploadRespDTO completeUpload(String sessionId) {
        UploadSessionDoc session = claimSessionForMerge(sessionId);
        if (session == null) {
            return buildIdempotentCompleteResponse(sessionId);
        }
        if (session.getChunks() == null || session.getChunks().isEmpty()) {
            releaseMergeClaim(sessionId);
            throw new BusinessException(ResultCode.PARAM_INVALID, "没有可合并的分片");
        }

        // 按索引升序合并分片
        List<ComposeSource> sources = session.getChunks().stream()
                .sorted(Comparator.naturalOrder())
                .map(idx -> ComposeSource.builder()
                        .bucket(minioConfig.getRawBucket())
                        .object(chunkObject(sessionId, idx))
                        .build())
                .collect(Collectors.toList());
        try {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket())
                    .object("raw/" + sessionId + "/merged.mp4")
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            releaseMergeClaim(sessionId);
            log.error("合并分片失败: sessionId={}, chunks={}", sessionId, session.getChunks().size(), e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片合并失败");
        }
        if (!markSessionUploaded(sessionId)) {
            log.warn("合并已完成但会话状态未从 merging 更新为 uploaded: sessionId={}", sessionId);
        }

        VideoMetaDoc meta = videoProcessService.createMetaForMergedUpload(sessionId);
        uploadSessionCleanupService.finalizeSessionAfterMerge(sessionId);
        videoProcessService.processSession(sessionId, meta.getVideoId());

        log.info("分片合并完成，进入后台处理: sessionId={}, videoId={}", sessionId, meta.getVideoId());
        return new CompleteUploadRespDTO(sessionId, meta.getVideoId(), VideoConstants.SESSION_UPLOADED);
    }

    @Override
    public UploadSessionDoc getSession(String sessionId) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "上传会话不存在或已过期");
        }
        return session;
    }

    @Override
    public UploadRespDTO uploadDirect(Long uploaderId, String uploaderType, String sourceType, MultipartFile file) {
        String limitType = resolveLimitType(uploaderType);
        validateSize(file.getSize(), limitType);

        UploadRespDTO result = videoProcessService.processFile(
                uploaderId, uploaderType, sourceType, file);
        log.info("单次直传完成: videoId={}, status={}, failReason={}",
                result.getVideoId(), result.getStatus(), result.getFailReason());
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 校验文件大小上限。
     */
    private void validateSize(Long fileSize, String limitType) {
        long maxSize = VideoConstants.UPLOADER_ADMIN.equals(limitType)
                ? VideoConstants.MAX_SIZE_ADMIN : VideoConstants.MAX_SIZE_COMMENT;
        if (fileSize != null && fileSize > maxSize) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    VideoConstants.UPLOADER_ADMIN.equals(limitType) ? "文件过大（管理端上限 2GB）" : "文件过大（评论区上限 100MB）");
        }
    }

    /**
     * 解析校验档位：admin → admin 上限，其余 → comment 上限。
     */
    private String resolveLimitType(String uploaderType) {
        return VideoConstants.UPLOADER_ADMIN.equals(uploaderType)
                ? VideoConstants.UPLOADER_ADMIN : VideoConstants.UPLOADER_USER;
    }

    /**
     * 查询会话并校验存在。
     */
    private UploadSessionDoc requireSession(String sessionId) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "上传会话不存在或已过期");
        }
        return session;
    }

    /**
     * 分片对象路径：raw/{sessionId}/chunk_{index}。
     */
    private String chunkObject(String sessionId, Integer chunkIndex) {
        return "raw/" + sessionId + "/chunk_" + chunkIndex;
    }

    /**
     * 原子抢占合并：仅当 status=uploading 时改为 merging，保证同一时刻只有一个 complete 执行 MinIO 合并。
     *
     * @return 抢占成功时会话；未抢占到返回 null（由调用方做幂等或提示）
     */
    private UploadSessionDoc claimSessionForMerge(String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId)
                .and("status").is(VideoConstants.SESSION_UPLOADING));
        Update update = new Update().set("status", VideoConstants.SESSION_MERGING);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true),
                UploadSessionDoc.class);
    }

    /** 合并失败时释放 merging，允许客户端重试 complete。 */
    private void releaseMergeClaim(String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId)
                .and("status").is(VideoConstants.SESSION_MERGING));
        Update update = new Update().set("status", VideoConstants.SESSION_UPLOADING);
        mongoTemplate.updateFirst(query, update, UploadSessionDoc.class);
    }

    /** 合并成功后 merging → uploaded（条件更新）。 */
    private boolean markSessionUploaded(String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId)
                .and("status").is(VideoConstants.SESSION_MERGING));
        Update update = new Update().set("status", VideoConstants.SESSION_UPLOADED);
        return mongoTemplate.updateFirst(query, update, UploadSessionDoc.class).getModifiedCount() > 0;
    }

    /**
     * 并发 complete 的幂等响应：已成功合并或已进入后台处理的会话直接返回当前状态，不再触发合并/转码。
     */
    private CompleteUploadRespDTO buildIdempotentCompleteResponse(String sessionId) {
        UploadSessionDoc current = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (current == null) {
            VideoMetaDoc meta = findMetaBySessionId(sessionId);
            if (meta != null) {
                return new CompleteUploadRespDTO(sessionId, meta.getVideoId(), VideoConstants.SESSION_UPLOADED);
            }
            throw new BusinessException(ResultCode.NOT_FOUND, "上传会话不存在或已过期");
        }
        String status = current.getStatus();
        if (VideoConstants.SESSION_UPLOADED.equals(status)
                || VideoConstants.SESSION_PROCESSED.equals(status)) {
            Long videoId = current.getVideoId();
            if (videoId == null) {
                VideoMetaDoc meta = findMetaBySessionId(sessionId);
                videoId = meta != null ? meta.getVideoId() : null;
            }
            return new CompleteUploadRespDTO(sessionId, videoId, status);
        }
        if (VideoConstants.SESSION_MERGING.equals(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分片合并处理中，请稍后刷新");
        }
        if (VideoConstants.SESSION_FAILED.equals(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "上传会话已失败，请重新上传");
        }
        if (VideoConstants.SESSION_CLEANUP_FAILED.equals(status)) {
            VideoMetaDoc meta = findMetaBySessionId(sessionId);
            if (meta != null) {
                return new CompleteUploadRespDTO(sessionId, meta.getVideoId(), status);
            }
            throw new BusinessException(ResultCode.SERVER_ERROR,
                    "视频已处理，分片清理未完成，请稍后重试或联系运维");
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "会话已处理完成，请勿重复提交");
    }

    private VideoMetaDoc findMetaBySessionId(String sessionId) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("sessionId").is(sessionId)), VideoMetaDoc.class);
    }
}
