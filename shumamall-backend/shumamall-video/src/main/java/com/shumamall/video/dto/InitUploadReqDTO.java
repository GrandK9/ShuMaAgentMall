package com.shumamall.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 初始化分片上传请求 DTO。
 */
@Data
public class InitUploadReqDTO {

    /** 原始文件名，如 demo.mp4 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件总大小（字节） */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须为正数")
    private Long fileSize;

    /** MIME 类型（可选） */
    private String mimeType;

    /** 文件哈希（可选，断点续传校验用） */
    private String hash;

    /** 上传者类型 admin / user，决定校验档位与时长/大小上限 */
    @NotBlank(message = "上传者类型不能为空")
    private String uploaderType;

    /** 上传来源 browser / miniapp / api，默认 browser */
    private String sourceType;
}
