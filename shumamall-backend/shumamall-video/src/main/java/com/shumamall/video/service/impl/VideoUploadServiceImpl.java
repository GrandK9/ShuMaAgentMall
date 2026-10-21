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
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
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

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    /** 前端据此判断这一片要重传，而不是整次上传作废。 */
    static final String CHUNK_CHECKSUM_MISMATCH = "分片校验未通过";

    /** 片号不连续或字节数对不上时拒绝合并，会话退回 uploading，已传的片可以续传。 */
    static final String CHUNK_LAYOUT_MISMATCH = "分片不完整或大小不符，无法合并";

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
    public void uploadChunk(String sessionId, Integer chunkIndex, MultipartFile data, String md5) {
        UploadSessionDoc session = requireSession(sessionId);
        if (!VideoConstants.SESSION_UPLOADING.equals(session.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "会话已处理完成，无法继续上传");
        }
        if (session.getChunks().contains(chunkIndex)) {
            log.debug("分片已存在，跳过重复上传: sessionId={}, chunk={}", sessionId, chunkIndex);
            return;
        }
        String expected = normalizeMd5(md5);
        byte[] body = readChunk(data);
        String actual = md5Hex(body);
        // 先对上浏览器传来的摘要，再写入。对不上说明这一片在传到服务端时已经变了，不能记索引。
        if (!actual.equals(expected)) {
            log.warn("分片与浏览器摘要不一致: sessionId={}, chunk={}", sessionId, chunkIndex);
            throw new BusinessException(ResultCode.PARAM_INVALID, CHUNK_CHECKSUM_MISMATCH);
        }
        String etag;
        try {
            // 不额外指定 partSize。一片不超过 5MB，SDK 按单次写入，ETag 就是内容的 MD5。
            ObjectWriteResponse written = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket())
                    .object(chunkObject(sessionId, chunkIndex))
                    .stream(new ByteArrayInputStream(body), body.length, -1)
                    .contentType(data.getContentType())
                    .build());
            etag = written.etag() == null ? "" : written.etag().replace("\"", "").toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            log.error("分片上传 MinIO 失败: sessionId={}, chunk={}", sessionId, chunkIndex, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片上传失败");
        }
        // 对象已经落下，但 ETag 和收到的字节不一致时不记索引。同一路径重传会盖掉这一片。
        if (!actual.equals(etag)) {
            log.warn("分片写入后摘要不一致: sessionId={}, chunk={}, etag={}", sessionId, chunkIndex, etag);
            throw new BusinessException(ResultCode.PARAM_INVALID, CHUNK_CHECKSUM_MISMATCH);
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
        try {
            // 合并只按片号拼接。先核对片齐全、非末片都是 5MB、总字节数等于申报大小，否则会把缺片或短片拼进去。
            assertChunksComplete(session);
        } catch (BusinessException e) {
            releaseMergeClaim(sessionId);
            throw e;
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
     * 片号必须是 0 到最后一片，且除最后一片外每片都是约定的 5MB，各片大小之和等于初始化时的 fileSize。
     * 大小不符的片会从对象存储和片号列表里去掉，续传才不会把它当成已经完成。
     */
    private void assertChunksComplete(UploadSessionDoc session) {
        Long fileSize = session.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "上传文件大小无效");
        }
        int chunkSize = VideoConstants.CHUNK_SIZE;
        int expectedCount = (int) ((fileSize + chunkSize - 1) / chunkSize);
        List<Integer> indexes = session.getChunks() == null
                ? List.of()
                : session.getChunks().stream().filter(index -> index != null).distinct().sorted().toList();
        List<Integer> mismatched = new ArrayList<>();
        long total = 0;
        int matched = 0;
        for (Integer index : indexes) {
            if (index < 0 || index >= expectedCount) {
                mismatched.add(index);
                continue;
            }
            long expected = expectedChunkSize(fileSize, chunkSize, expectedCount, index);
            Long size = readChunkSize(session.getSessionId(), index);
            if (size == null || size != expected) {
                log.warn("分片大小不符: sessionId={}, chunk={}, size={}, expected={}",
                        session.getSessionId(), index, size, expected);
                mismatched.add(index);
                continue;
            }
            total += size;
            matched++;
        }
        if (!mismatched.isEmpty()) {
            discardChunks(session.getSessionId(), mismatched);
        }
        if (matched != expectedCount || total != fileSize) {
            throw new BusinessException(ResultCode.PARAM_INVALID, CHUNK_LAYOUT_MISMATCH);
        }
    }

    private long expectedChunkSize(long fileSize, int chunkSize, int expectedCount, int index) {
        boolean last = index == expectedCount - 1;
        return last ? fileSize - (long) (expectedCount - 1) * chunkSize : chunkSize;
    }

    /** 读不到大小时返回 null，调用方会把这一片从已完成列表里拿掉。 */
    private Long readChunkSize(String sessionId, int chunkIndex) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket())
                    .object(chunkObject(sessionId, chunkIndex))
                    .build());
            return stat == null ? null : stat.size();
        } catch (Exception e) {
            log.warn("读取分片大小失败: sessionId={}, chunk={}, error={}", sessionId, chunkIndex, e.getMessage());
            return null;
        }
    }

    private void discardChunks(String sessionId, List<Integer> indexes) {
        Set<Integer> unique = new HashSet<>(indexes);
        for (Integer index : unique) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getRawBucket())
                        .object(chunkObject(sessionId, index))
                        .build());
            } catch (Exception e) {
                log.warn("删除大小不符的分片失败: sessionId={}, chunk={}, error={}",
                        sessionId, index, e.getMessage());
            }
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(sessionId)),
                new Update().pullAll("chunks", unique.toArray()),
                UploadSessionDoc.class);
    }

    private byte[] readChunk(MultipartFile data) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分片内容为空");
        }
        byte[] body;
        try {
            body = data.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分片内容读取失败");
        }
        if (body.length > VideoConstants.CHUNK_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "分片超过 5MB");
        }
        return body;
    }

    private String normalizeMd5(String md5) {
        if (!StringUtils.hasText(md5)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分片摘要不能为空");
        }
        String hex = md5.trim().toLowerCase(Locale.ROOT);
        if (!hex.matches("[0-9a-f]{32}")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分片摘要格式不正确");
        }
        return hex;
    }

    private String md5Hex(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(body);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片摘要计算失败");
        }
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
