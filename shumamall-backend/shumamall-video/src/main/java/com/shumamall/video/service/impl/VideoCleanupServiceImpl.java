package com.shumamall.video.service.impl;

import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.service.UploadSessionCleanupService;
import com.shumamall.video.service.VideoCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 上传会话残留清理：重试 {@code cleanup_failed} 与过期未完成上传，不再全量扫描 MinIO raw。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCleanupServiceImpl implements VideoCleanupService {

    private final UploadSessionCleanupService uploadSessionCleanupService;

    @Override
    @Scheduled(fixedDelay = VideoConstants.CLEANUP_INTERVAL_MS)
    public void cleanupExpiredSessions() {
        try {
            uploadSessionCleanupService.retryPendingSessionCleanups();
        } catch (Exception e) {
            log.error("上传会话清理任务失败", e);
        }
    }
}
