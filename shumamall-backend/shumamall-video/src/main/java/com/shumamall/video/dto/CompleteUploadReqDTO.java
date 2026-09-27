package com.shumamall.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并分片请求 DTO。
 */
@Data
public class CompleteUploadReqDTO {

    /** 会话 ID */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
}
