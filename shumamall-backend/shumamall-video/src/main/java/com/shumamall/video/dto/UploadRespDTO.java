package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 直传（单次上传）响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadRespDTO {

    /**
     * 视频 ID。雪花 ID 超出 JS 安全整数范围，必须按字符串下发，否则前端解析即失真。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 处理状态 validating / transcoded / failed */
    private String status;

    /** 播放清单路径（转码完成后非空） */
    private String playlistUrl;

    /** 失败原因（失败时非空） */
    private String failReason;
}
