package com.shumamall.video.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频元数据文档（MongoDB 集合：video_metadata）。
 * <p>
 * 元数据结构与架构文档一致：uploaderType/sourceType/status/format/duration/
 * resolution/fileSize/playlistUrl/hlsSegments/thumbnailUrl。
 * 二进制本体与 HLS 分片存 MinIO，此处仅存索引与播放清单路径。
 */
@Data
@Document(collection = "video_metadata")
@CompoundIndexes({
        @CompoundIndex(name = "idx_uploader_time", def = "{'uploaderId': 1, 'uploadedAt': -1}"),
        @CompoundIndex(name = "idx_status_time", def = "{'status': 1, 'uploadedAt': -1}")
})
public class VideoMetaDoc {

    /** 视频 ID（雪花 ID，与全项目主键策略一致） */
    @Id
    private Long videoId;

    /** 分片上传会话 ID（直传时为空） */
    private String sessionId;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 上传者类型 admin / user */
    private String uploaderType;

    /** 上传来源 browser / miniapp / api */
    private String sourceType;

    /** 校验档位 comment / admin */
    private String limitType;

    /** 处理状态 uploading / validating / transcoded / failed */
    private String status;

    /** 视频编码格式 h264 / h265 */
    private String format;

    /** 音频编码 aac / mp3 */
    private String audioCodec;

    /** 时长（秒） */
    private Double duration;

    /** 分辨率宽 */
    private Integer width;

    /** 分辨率高 */
    private Integer height;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 原始文件对象路径（MinIO raw bucket 内） */
    private String rawObject;

    /** HLS 播放清单路径（video-hls bucket 内，如 hls/{videoId}/index.m3u8） */
    private String playlistUrl;

    /** HLS 分片列表 */
    private List<HlsSegment> hlsSegments = new ArrayList<>();

    /** 缩略图路径（video-thumb bucket 内，可为空） */
    private String thumbnailUrl;

    /** 失败原因（校验/切片失败时填写） */
    private String failReason;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** 转码完成时间 */
    private LocalDateTime transcodedAt;

    /**
     * HLS 分片项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HlsSegment {

        /** 分片序号 */
        private Integer index;

        /** 分片时长（秒） */
        private Double duration;

        /** 分片对象路径（video-hls bucket 内） */
        private String url;
    }
}
