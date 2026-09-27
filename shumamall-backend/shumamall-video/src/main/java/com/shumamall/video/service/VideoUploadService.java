package com.shumamall.video.service;

import com.shumamall.video.dto.CompleteUploadRespDTO;
import com.shumamall.video.dto.InitUploadReqDTO;
import com.shumamall.video.dto.InitUploadRespDTO;
import com.shumamall.video.dto.UploadRespDTO;
import com.shumamall.video.entity.UploadSessionDoc;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频上传服务接口。
 * <p>
 * 支持两种上传方式：
 * <ul>
 *   <li><b>分片上传</b>（管理端大文件）：initUpload → uploadChunk（可并发/断点续传）→ completeUpload</li>
 *   <li><b>单次直传</b>（评论区小文件）：uploadDirect 一次上传，服务端同步校验 + 切片</li>
 * </ul>
 */
public interface VideoUploadService {

    /**
     * 初始化分片上传会话（MongoDB 存会话，TTL 24h 自动过期）。
     *
     * @param uploaderId 上传者用户 ID
     * @param dto        初始化参数
     * @return 会话信息
     */
    InitUploadRespDTO initUpload(Long uploaderId, InitUploadReqDTO dto);

    /**
     * 上传单个分片（每片 5MB，直接写入 MinIO raw bucket）。
     *
     * @param sessionId  会话 ID
     * @param chunkIndex 分片索引
     * @param data       分片内容
     */
    void uploadChunk(String sessionId, Integer chunkIndex, MultipartFile data);

    /**
     * 合并分片（MinIO composeObject）并触发后台校验 + HLS 切片。
     *
     * @param sessionId 会话 ID
     * @return 合并结果（videoId 可能处理中为空）
     */
    CompleteUploadRespDTO completeUpload(String sessionId);

    /**
     * 查询上传会话（断点续传：返回已传分片索引列表）。
     *
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    UploadSessionDoc getSession(String sessionId);

    /**
     * 单次直传（评论区小文件：≤100MB / 60s，同步校验 + 切片后返回）。
     *
     * @param uploaderId   上传者用户 ID
     * @param uploaderType 上传者类型 admin / user
     * @param sourceType   上传来源 browser / miniapp / api
     * @param file         视频文件
     * @return 处理结果（含 videoId / 状态 / 失败原因）
     */
    UploadRespDTO uploadDirect(Long uploaderId, String uploaderType, String sourceType, MultipartFile file);
}
