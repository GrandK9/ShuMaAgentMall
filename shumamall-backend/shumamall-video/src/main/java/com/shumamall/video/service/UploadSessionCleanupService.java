package com.shumamall.video.service;

/**
 * 分片上传会话收尾：合并且元数据已创建后删除 MinIO {@code chunk_*} 并移除 {@code upload_session}。
 */
public interface UploadSessionCleanupService {

    /**
     * 合并完成且 meta 已创建后调用：删除分片、校验、移除会话文档；失败则标记 {@code cleanup_failed}。
     *
     * @param sessionId 上传会话 ID，为空则跳过
     */
    void finalizeSessionAfterMerge(String sessionId);

    /**
     * 重试 {@code cleanup_failed} 及过期未完成上传的会话清理（由定时任务触发）。
     */
    void retryPendingSessionCleanups();
}
