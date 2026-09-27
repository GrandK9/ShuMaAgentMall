package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合并分片响应 DTO。
 * <p>
 * 合并成功后后台异步执行 ffprobe 校验 + HLS 切片，
 * 前端可轮询 {@code GET /api/v1/video/meta/{videoId}} 获取最终处理状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRespDTO {

    /** 会话 ID */
    private String sessionId;

    /**
     * 视频 ID（后台处理创建后回填，处理中可能为空）。
     * <p>
     * 雪花 ID 超出 JS 安全整数范围，必须按字符串下发，否则前端解析即失真。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 会话状态 uploaded / processed */
    private String status;
}
