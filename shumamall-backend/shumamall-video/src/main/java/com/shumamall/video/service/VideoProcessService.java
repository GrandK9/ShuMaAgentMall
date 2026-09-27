package com.shumamall.video.service;

import com.shumamall.video.dto.UploadRespDTO;
import com.shumamall.video.entity.VideoMetaDoc;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频处理服务接口。
 * <p>
 * 核心流程：ffprobe 格式校验（编码/分辨率/时长/大小）→ FFmpeg HLS 切片 →
 * 切片上传 MinIO → 更新 MongoDB 元数据。重转码管线仅作为第三方 API 上传的兜底。
 */
public interface VideoProcessService {

    /**
     * 异步处理分片上传会话（合并完成后触发）。
     * <p>
     * 流程：下载合并文件 → 创建元数据 → 校验 → HLS 切片 → 回填会话 videoId。
     *
     * @param sessionId 会话 ID
     */
    void processSession(String sessionId);

    /**
     * 同步处理单次直传文件（评论区小文件场景）。
     *
     * @param uploaderId   上传者用户 ID
     * @param uploaderType 上传者类型 admin / user
     * @param sourceType   上传来源 browser / miniapp / api
     * @param file         视频文件
     * @return 处理结果（含 videoId / 状态 / 失败原因）
     */
    UploadRespDTO processFile(Long uploaderId, String uploaderType, String sourceType, MultipartFile file);

    /**
     * 重新转码（管理端兜底：下载原始文件重新校验 + 切片）。
     *
     * @param meta 视频元数据
     */
    void reTranscode(VideoMetaDoc meta);
}
