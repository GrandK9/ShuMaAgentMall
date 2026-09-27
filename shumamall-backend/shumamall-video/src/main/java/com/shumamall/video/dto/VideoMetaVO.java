package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频元数据视图对象（管理端列表/详情返回）。
 */
@Data
public class VideoMetaVO {

    /**
     * 视频 ID。
     * <p>
     * 雪花 ID 是 19 位长整型，超过 JS 的 {@code Number.MAX_SAFE_INTEGER}（2^53-1），
     * 按 JSON 数字下发会在浏览器解析时丢失末位精度（实测 2100527804956696578 变成 2100527804956696600），
     * 前端再拿这个 ID 调 /play、/meta 就会「视频不存在」。故一律序列化为字符串。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

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

    /** 时长（秒） */
    private Double duration;

    /** 分辨率宽 */
    private Integer width;

    /** 分辨率高 */
    private Integer height;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 播放清单路径 */
    private String playlistUrl;

    /** 缩略图路径 */
    private String thumbnailUrl;

    /** 失败原因 */
    private String failReason;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** 转码完成时间 */
    private LocalDateTime transcodedAt;

    /** HLS 分片列表 */
    private List<HlsSegmentVO> hlsSegments = new ArrayList<>();

    /**
     * HLS 分片视图对象。
     */
    @Data
    public static class HlsSegmentVO {

        /** 分片序号 */
        private Integer index;

        /** 分片时长（秒） */
        private Double duration;

        /** 分片对象路径 */
        private String url;
    }
}
