package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放进度查询响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoProgressRespDTO {

    /**
     * 视频 ID。雪花 ID 超出 JS 安全整数范围，必须按字符串下发，否则前端解析即失真。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 上次播放位置（秒，无记录时为 0） */
    private Double position;
}
