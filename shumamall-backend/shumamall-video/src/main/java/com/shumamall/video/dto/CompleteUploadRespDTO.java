package com.shumamall.video.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合并分片响应 DTO。
 * <p>
 * 合并成功后返回 {@code videoId}，前端轮询 {@code GET /api/v1/video/meta/{videoId}} 获取转码结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRespDTO {

    /** 会话 ID */
    private String sessionId;

    /** 视频 ID（合并完成时创建元数据并返回） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 会话状态 uploaded / processed */
    private String status;
}
