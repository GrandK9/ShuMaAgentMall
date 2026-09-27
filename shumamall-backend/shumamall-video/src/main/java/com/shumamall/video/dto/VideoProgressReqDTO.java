package com.shumamall.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 播放进度上报请求 DTO。
 */
@Data
public class VideoProgressReqDTO {

    /** 视频 ID */
    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    /** 当前播放位置（秒） */
    @NotNull(message = "播放位置不能为空")
    @Min(value = 0, message = "播放位置不能为负")
    private Double position;

    /** 视频总时长（秒，可选） */
    @Positive(message = "总时长必须为正数")
    private Double totalDuration;
}
