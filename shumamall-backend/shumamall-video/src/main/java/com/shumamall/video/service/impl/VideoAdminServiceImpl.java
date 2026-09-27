package com.shumamall.video.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.dto.VideoMetaVO;
import com.shumamall.video.dto.VideoPageQuery;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.service.VideoAdminService;
import com.shumamall.video.service.VideoProcessService;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 视频管理端服务实现（列表 / 删除 / 重新转码）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAdminServiceImpl implements VideoAdminService {

    private final MongoTemplate mongoTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final VideoProcessService videoProcessService;

    @Override
    public PageResult<VideoMetaVO> page(VideoPageQuery query) {
        Query q = new Query();
        if (StringUtils.hasText(query.getStatus())) {
            q.addCriteria(Criteria.where("status").is(query.getStatus()));
        }
        if (StringUtils.hasText(query.getUploaderType())) {
            q.addCriteria(Criteria.where("uploaderType").is(query.getUploaderType()));
        }
        long total = mongoTemplate.count(q, VideoMetaDoc.class);

        q.with(Sort.by(Sort.Direction.DESC, "uploadedAt"))
                .skip((long) (query.getPage() - 1) * query.getSize())
                .limit(query.getSize());
        List<VideoMetaDoc> records = mongoTemplate.find(q, VideoMetaDoc.class);

        List<VideoMetaVO> list = records.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>((long) query.getPage(), (long) query.getSize(), total, list);
    }

    @Override
    public void delete(Long videoId) {
        VideoMetaDoc meta = mongoTemplate.findById(videoId, VideoMetaDoc.class);
        if (meta == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频不存在");
        }
        // 清理 MinIO 原始文件
        if (StringUtils.hasText(meta.getRawObject())) {
            removeObjectIfExists(minioConfig.getRawBucket(), meta.getRawObject());
        }
        // 清理 HLS 分片（前缀匹配删除）
        removeObjectsByPrefix(minioConfig.getHlsBucket(), "hls/" + videoId + "/");
        // 清理缩略图
        if (StringUtils.hasText(meta.getThumbnailUrl())) {
            removeObjectIfExists(minioConfig.getThumbBucket(), meta.getThumbnailUrl());
        }
        mongoTemplate.remove(meta);
        log.info("视频已删除: videoId={}, uploaderId={}", videoId, meta.getUploaderId());
    }

    @Override
    public void reTranscode(Long videoId) {
        VideoMetaDoc meta = mongoTemplate.findById(videoId, VideoMetaDoc.class);
        if (meta == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视频不存在");
        }
        if (!StringUtils.hasText(meta.getRawObject())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该视频无原始文件，无法重新转码");
        }
        videoProcessService.reTranscode(meta);
        log.info("触发重新转码: videoId={}", videoId);
    }

    // ==================== 私有方法 ====================

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
     * 删除单个对象（不存在时静默忽略）。
     */
    private void removeObjectIfExists(String bucket, String object) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(object).build());
        } catch (Exception e) {
            log.warn("MinIO 对象删除失败（忽略）: bucket={}, object={}, error={}",
                    bucket, object, e.getMessage());
        }
    }

    /**
     * 按前缀批量删除对象（HLS 分片清理）。
     */
    private void removeObjectsByPrefix(String bucket, String prefix) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build());
            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(objectName).build());
            }
        } catch (Exception e) {
            log.warn("MinIO 前缀删除失败（忽略）: bucket={}, prefix={}, error={}",
                    bucket, prefix, e.getMessage());
        }
    }
}
