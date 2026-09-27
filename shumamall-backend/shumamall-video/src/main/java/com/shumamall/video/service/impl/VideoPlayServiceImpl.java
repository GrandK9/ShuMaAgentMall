package com.shumamall.video.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dto.VideoMetaVO;
import com.shumamall.video.dto.VideoPlayRespDTO;
import com.shumamall.video.dto.VideoProgressReqDTO;
import com.shumamall.video.dto.VideoProgressRespDTO;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.entity.VideoProgressDoc;
import com.shumamall.video.service.VideoPlayService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频播放与断点续播服务实现。
 * <p>
 * 播放：MinIO 生成临时签名 URL（1 小时有效），播放器直接请求 MinIO。
 * 断点续播：前端每 10 秒上报 → 高频写 Redis（TTL 30min）；
 * 定时任务把内存累积的进度批量落库 MongoDB，读取时 Redis 优先、Mongo 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPlayServiceImpl implements VideoPlayService {

    private final MongoTemplate mongoTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final StringRedisTemplate stringRedisTemplate;

    /** 内存累积的播放进度（key=userId:videoId），定时 flush 到 MongoDB */
    private final Map<String, Double> pendingProgress = new ConcurrentHashMap<>();

    /** 播放进度 Redis key 前缀 */
    private static final String PROGRESS_KEY_PREFIX = "video:progress:";

    /** 播放进度 Redis TTL（秒） */
    private static final long PROGRESS_TTL_SECONDS = 1800;

    @Override
    public VideoPlayRespDTO getPlayUrl(Long videoId) {
        VideoMetaDoc meta = requireMeta(videoId);
        if (!VideoConstants.STATUS_TRANSCODED.equals(meta.getStatus())) {
            return new VideoPlayRespDTO(videoId, meta.getStatus(), null,
                    meta.getDuration(), null, meta.getFailReason(), null);
        }
        try {
            String playlistUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getHlsBucket())
                            .object(meta.getPlaylistUrl())
                            .expiry(VideoConstants.PLAY_URL_EXPIRY_SECONDS)
                            .build());
            String thumbUrl = StringUtils.hasText(meta.getThumbnailUrl())
                    ? minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getThumbBucket())
                            .object(meta.getThumbnailUrl())
                            .expiry(VideoConstants.PLAY_URL_EXPIRY_SECONDS)
                            .build())
                    : null;
            List<VideoPlayRespDTO.SegmentUrlVO> segmentUrls = signSegmentUrls(meta);
            return new VideoPlayRespDTO(videoId, meta.getStatus(), playlistUrl,
                    meta.getDuration(), thumbUrl, null, segmentUrls);
        } catch (Exception e) {
            log.error("生成播放签名 URL 失败: videoId={}", videoId, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "播放地址生成失败");
        }
    }

    @Override
    public VideoMetaVO getMeta(Long videoId) {
        VideoMetaDoc meta = requireMeta(videoId);
        return toVO(meta);
    }

    @Override
    public void reportProgress(Long userId, VideoProgressReqDTO dto) {
        String key = progressKey(userId, dto.getVideoId());
        // 高频写入 Redis（带 TTL）
        stringRedisTemplate.opsForValue().set(key,
                dto.getPosition() + "|" + LocalDateTime.now(), Duration.ofSeconds(PROGRESS_TTL_SECONDS));
        // 累积到内存，定时 flush 到 MongoDB
        pendingProgress.put(key, dto.getPosition());
    }

    @Override
    public VideoProgressRespDTO getProgress(Long userId, Long videoId) {
        String key = progressKey(userId, videoId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(value)) {
            String position = value.split("\\|")[0];
            try {
                return new VideoProgressRespDTO(videoId, Double.parseDouble(position));
            } catch (NumberFormatException ignored) {
                // 解析失败走 Mongo 兜底
            }
        }
        VideoProgressDoc doc = mongoTemplate.findOne(
                new Query(Criteria.where("userId").is(userId).and("videoId").is(videoId)),
                VideoProgressDoc.class);
        return new VideoProgressRespDTO(videoId, doc != null ? doc.getPosition() : 0.0);
    }

    /**
     * 定时任务：每 30 秒把内存累积的播放进度批量 upsert 到 MongoDB。
     */
    @Scheduled(fixedRate = 30000)
    public void flushPendingProgress() {
        if (pendingProgress.isEmpty()) {
            return;
        }
        Map<String, Double> snapshot = new ConcurrentHashMap<>(pendingProgress);
        pendingProgress.clear();
        snapshot.forEach((key, position) -> {
            try {
                String[] parts = key.split(":");
                Long userId = Long.parseLong(parts[parts.length - 2]);
                Long videoId = Long.parseLong(parts[parts.length - 1]);
                String id = userId + ":" + videoId;
                VideoProgressDoc doc = mongoTemplate.findById(id, VideoProgressDoc.class);
                if (doc == null) {
                    doc = new VideoProgressDoc();
                    doc.setId(id);
                    doc.setUserId(userId);
                    doc.setVideoId(videoId);
                }
                doc.setPosition(position);
                doc.setUpdatedAt(LocalDateTime.now());
                mongoTemplate.save(doc);
            } catch (Exception e) {
                log.warn("播放进度落库失败: key={}", key, e);
            }
        });
    }

    // ==================== 私有方法 ====================

    /**
     * 查询视频元数据并校验存在。
     */
    private VideoMetaDoc requireMeta(Long videoId) {
        VideoMetaDoc meta = mongoTemplate.findById(videoId, VideoMetaDoc.class);
        if (meta == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频不存在");
        }
        return meta;
    }

    /**
     * 元数据转视图对象。
     */
    private VideoMetaVO toVO(VideoMetaDoc meta) {
        VideoMetaVO vo = new VideoMetaVO();
        vo.setVideoId(meta.getVideoId());
        vo.setUploaderId(meta.getUploaderId());
        vo.setUploaderType(meta.getUploaderType());
        vo.setSourceType(meta.getSourceType());
        vo.setLimitType(meta.getLimitType());
        vo.setStatus(meta.getStatus());
        vo.setFormat(meta.getFormat());
        vo.setDuration(meta.getDuration());
        vo.setWidth(meta.getWidth());
        vo.setHeight(meta.getHeight());
        vo.setFileSize(meta.getFileSize());
        vo.setPlaylistUrl(meta.getPlaylistUrl());
        vo.setThumbnailUrl(meta.getThumbnailUrl());
        vo.setFailReason(meta.getFailReason());
        vo.setUploadedAt(meta.getUploadedAt());
        vo.setTranscodedAt(meta.getTranscodedAt());
        if (meta.getHlsSegments() != null) {
            meta.getHlsSegments().forEach(s -> {
                VideoMetaVO.HlsSegmentVO sv = new VideoMetaVO.HlsSegmentVO();
                sv.setIndex(s.getIndex());
                sv.setDuration(s.getDuration());
                sv.setUrl(s.getUrl());
                vo.getHlsSegments().add(sv);
            });
        }
        return vo;
    }

    /**
     * 为全部 .ts 分片生成签名 URL。
     * <p>
     * HLS bucket 私有，m3u8 内的相对分片路径无法直接请求，
     * 这里一次性为每个分片签发与 playlistUrl 同效期的签名 URL，
     * 供前端 hls.js 自定义 loader 按分片 index 映射替换。
     *
     * @param meta 视频元数据
     * @return 分片签名 URL 列表（无分片时为空列表）
     */
    private List<VideoPlayRespDTO.SegmentUrlVO> signSegmentUrls(VideoMetaDoc meta) {
        List<VideoPlayRespDTO.SegmentUrlVO> urls = new ArrayList<>();
        if (meta.getHlsSegments() == null) {
            return urls;
        }
        for (VideoMetaDoc.HlsSegment segment : meta.getHlsSegments()) {
            try {
                String signed = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(minioConfig.getHlsBucket())
                                .object(segment.getUrl())
                                .expiry(VideoConstants.PLAY_URL_EXPIRY_SECONDS)
                                .build());
                urls.add(new VideoPlayRespDTO.SegmentUrlVO(segment.getIndex(), signed));
            } catch (Exception e) {
                log.warn("分片签名 URL 生成失败: videoId={}, segment={}", meta.getVideoId(), segment.getUrl(), e);
            }
        }
        return urls;
    }

    /**
     * 进度 Redis key：video:progress:{userId}:{videoId}。
     */
    private String progressKey(Long userId, Long videoId) {
        return PROGRESS_KEY_PREFIX + userId + ":" + videoId;
    }
}
