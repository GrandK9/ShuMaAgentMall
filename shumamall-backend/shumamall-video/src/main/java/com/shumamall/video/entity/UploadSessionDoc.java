package com.shumamall.video.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分片上传会话文档（MongoDB 集合：upload_session）。
 * <p>
 * 管理端大文件分片上传的会话记录：前端每次上传前创建会话，
 * 每片直接写入 MinIO {@code raw/{sessionId}/chunk_{index}}，
 * 全部传完后 {@code completeUpload} 合并分片、创建 {@code video_metadata}、清理分片与会话并异步转码。
 * <p>
 * 转码成功后删除 MinIO 分片并移除本会话文档；{@code expireAt} 用于识别过期未完成上传。
 */
@Data
@Document(collection = "upload_session")
public class UploadSessionDoc {

    /** 会话 ID（UUID） */
    @Id
    private String sessionId;

    /** 原始文件名 */
    private String fileName;

    /** 文件总大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 文件哈希（可选，用于秒传/断点校验） */
    private String hash;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 上传者类型 admin / user */
    private String uploaderType;

    /** 上传来源 browser / miniapp / api */
    private String sourceType;

    /** 校验档位 comment / admin（决定时长/大小上限） */
    private String limitType;

    /** 会话状态 uploading / merging / uploaded / processed / failed / cleanup_failed */
    private String status;

    /** 会话失败原因（如转码队列已满） */
    private String failReason;

    /** 已上传的分片索引列表（断点续传查询用） */
    private List<Integer> chunks = new ArrayList<>();

    /**
     * 后台处理完成后回填的视频 ID。
     * <p>
     * 断点续传查询（{@code GET /api/v1/video/session/{sessionId}}）会直接把这个文档返回前端，
     * 而雪花 ID 超出 JS 安全整数范围，故按字符串下发（该注解只影响 Jackson 出参，
     * Spring Data Mongo 用 MappingMongoConverter 读写，不受影响）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 会话过期时间（未完成上传超时判定） */
    private LocalDateTime expireAt;
}
