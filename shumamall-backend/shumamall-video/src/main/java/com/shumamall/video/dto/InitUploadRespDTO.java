package com.shumamall.video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 初始化分片上传响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadRespDTO {

    /** 会话 ID（后续上传分片/合并均携带） */
    private String sessionId;

    /** 建议分片大小（字节，5MB） */
    private Integer chunkSize;

    /** 会话过期时间（24h 后 TTL 清理） */
    private LocalDateTime expireAt;
}
