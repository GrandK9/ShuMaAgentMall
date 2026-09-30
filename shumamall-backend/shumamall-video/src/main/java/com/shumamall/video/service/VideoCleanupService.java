package com.shumamall.video.service;

/**
 * 视频残留清理服务。
 * <p>
 * 负责定时清理 MinIO raw bucket 中过期上传会话的残留分片：
 * <ul>
 *   <li>合并成功后会同步删除分片，偶发失败或过期未完成上传仍可能残留 {@code chunk_*}；</li>
 *   <li>MongoDB TTL 到期会删除会话文档，但不会触发 MinIO 侧清理。</li>
 * </ul>
 * 清理以 MinIO raw 目录为权威：凡未被 {@code video_metadata.rawObject} 引用的
 * 对象（重转码依赖源文件）均视为残留删除，被引用的源文件始终保留。
 */
public interface VideoCleanupService {

    /**
     * 定时清理过期上传会话的 MinIO 残留对象与过期会话文档。
     * <p>
     * 由 {@code @Scheduled} 每 6 小时触发；单目录清理失败不影响其他目录。
     */
    void cleanupExpiredSessions();
}
