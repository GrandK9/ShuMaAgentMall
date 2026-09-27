package com.shumamall.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传分片请求 DTO。
 */
@Data
public class UploadChunkReqDTO {

    /** 会话 ID */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /** 分片索引（从 0 开始） */
    @NotNull(message = "分片索引不能为空")
    @Min(value = 0, message = "分片索引不能为负")
    private Integer chunkIndex;

    /** 分片二进制内容 */
    @NotNull(message = "分片内容不能为空")
    private MultipartFile data;
}
