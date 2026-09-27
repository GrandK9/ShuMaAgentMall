package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 播放地址响应 DTO。
 * <p>
 * 播放器拿到签名 playlist URL 后请求 MinIO。由于 HLS bucket 私有，
 * m3u8 中的相对分片路径无法直接访问，因此同时返回每个 .ts 分片的
 * 签名 URL 列表（与 playlistUrl 同效期），前端可用 hls.js 自定义 loader
 * 将分片相对路径映射为签名 URL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoPlayRespDTO {

    /**
     * 视频 ID。雪花 ID 超出 JS 安全整数范围，必须按字符串下发，否则前端解析即失真。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 处理状态 transcoded / failed */
    private String status;

    /** 签名后的 HLS 播放清单 URL（1 小时有效） */
    private String playlistUrl;

    /** 视频时长（秒） */
    private Double duration;

    /** 缩略图签名 URL（可为空） */
    private String thumbnailUrl;

    /** 失败原因（状态为 failed 时非空） */
    private String failReason;

    /** 各 .ts 分片的签名 URL（状态为 transcoded 时非空） */
    private List<SegmentUrlVO> segmentUrls;

    /**
     * 分片签名 URL 项。
     */
    @Data
    @AllArgsConstructor
    public static class SegmentUrlVO {

        /** 分片序号（对应 m3u8 中分片顺序） */
        private Integer index;

        /** 分片签名 URL */
        private String url;
    }
}
