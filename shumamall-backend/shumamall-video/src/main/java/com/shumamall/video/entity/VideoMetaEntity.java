package com.shumamall.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "video_metadata", autoResultMap = true)
public class VideoMetaEntity {

    @TableId(value = "video_id", type = IdType.INPUT)
    private Long videoId;

    private String sessionId;
    private Long uploaderId;
    private String uploaderType;
    private String sourceType;
    private String limitType;
    private String status;
    private String format;
    private String audioCodec;
    private Double duration;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String rawObject;
    private String playlistUrl;

    @TableField(value = "hls_segments", typeHandler = JacksonTypeHandler.class)
    private List<HlsSegmentItem> hlsSegments = new ArrayList<>();

    private String thumbnailUrl;
    private String failReason;
    private LocalDateTime uploadedAt;
    private LocalDateTime transcodedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HlsSegmentItem {
        private Integer index;
        private Double duration;
        private String url;
    }
}
