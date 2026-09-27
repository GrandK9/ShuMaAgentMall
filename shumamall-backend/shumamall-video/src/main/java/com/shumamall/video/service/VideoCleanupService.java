package com.shumamall.video.service;

/**
 * 视频残留清理服务。
 * <p>
 * 负责定时清理 MinIO raw bucket 中过期上传会话的残留分片：
 * <ul>
 *   <li>分片合并（composeObject）后 MinIO 不会自动删除源分片 {@code chunk_*}；</li>
 *   <li>MongoDB TTL 到期会删除会话文档，但不会触发 MinIO 侧清理。</li>
 * </ul>
 * 清理以 MinIO raw 目录为权威：凡未被 {@code video_metadata.rawObject} 引用的
 * 对象（重转码依赖源文件）均视为残留删除，被引用的源文件始终保留。
 */
public interface VideoCleanupService {

    /**
     * 定时清理过期上传会话的 MinIO 残留对象与过期会话文档。
     * <p>
     * 由 {@code @Scheduled} 每小时触发；单目录清理失败不影响其他目录。
     */
    void cleanupExpiredSessions();
}
