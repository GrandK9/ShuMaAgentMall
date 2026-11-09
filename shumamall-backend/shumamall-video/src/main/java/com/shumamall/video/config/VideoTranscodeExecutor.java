package com.shumamall.video.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dao.VideoMetaMapper;
import com.shumamall.video.dao.VideoUploadSessionMapper;
import com.shumamall.video.entity.VideoMetaEntity;
import com.shumamall.video.entity.VideoUploadSessionEntity;
import com.shumamall.video.support.ProcessTaskDeadline;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理端分片合并后的异步转码线程池：有界队列 + 拒绝时更新 MySQL 并抛出业务异常。
 */
@Slf4j
@Component
public class VideoTranscodeExecutor {

    private final VideoUploadSessionMapper uploadSessionMapper;
    private final VideoMetaMapper metaMapper;
    private final VideoProperties videoProperties;
    private final ThreadPoolExecutor executor;

    public VideoTranscodeExecutor(VideoUploadSessionMapper uploadSessionMapper,
                                  VideoMetaMapper metaMapper,
                                  VideoProperties videoProperties) {
        this.uploadSessionMapper = uploadSessionMapper;
        this.metaMapper = metaMapper;
        this.videoProperties = videoProperties;
        VideoProperties.Transcode transcode = videoProperties.getTranscode();
        int poolSize = transcode.getPoolSize();
        int queueCapacity = transcode.getQueueCapacity();
        int workers = Math.max(1, poolSize);
        int queueCap = Math.max(0, queueCapacity);
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "video-transcode-" + seq.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
        BlockingQueue<Runnable> queue = queueCap == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(queueCap);
        this.executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        log.info("视频转码线程池已初始化: poolSize={}, queueCapacity={}", workers, queueCap);
    }

    /**
     * 提交管理端分片上传的转码任务。
     *
     * @param sessionId 上传会话 ID（拒绝时写回 upload_session）
     * @param task        转码逻辑
     */
    public void scheduleSessionTranscode(String sessionId, Runnable task) {
        try {
            executor.execute(wrapWithTaskDeadline(task));
        } catch (RejectedExecutionException ex) {
            markSessionTranscodeBusy(sessionId);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, VideoConstants.TRANSCODE_BUSY_MESSAGE);
        }
    }

    /**
     * 提交重新转码任务（管理端兜底）。
     *
     * @param meta 视频元数据（拒绝时写回 failReason）
     * @param task 转码逻辑
     */
    public void scheduleRetranscode(VideoMetaEntity meta, Runnable task) {
        try {
            executor.execute(wrapWithTaskDeadline(task));
        } catch (RejectedExecutionException ex) {
            markMetaTranscodeBusy(meta);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, VideoConstants.TRANSCODE_BUSY_MESSAGE);
        }
    }

    private void markSessionTranscodeBusy(String sessionId) {
        VideoUploadSessionEntity session = uploadSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setStatus(VideoConstants.SESSION_FAILED);
            session.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
            uploadSessionMapper.updateById(session);
        }
        VideoMetaEntity meta = metaMapper.selectOne(new LambdaQueryWrapper<VideoMetaEntity>()
                .eq(VideoMetaEntity::getSessionId, sessionId));
        if (meta != null) {
            meta.setStatus(VideoConstants.STATUS_FAILED);
            meta.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
            metaMapper.updateById(meta);
        }
        log.warn("转码队列已满，已标记失败: sessionId={}", sessionId);
    }

    private void markMetaTranscodeBusy(VideoMetaEntity meta) {
        if (meta == null || meta.getVideoId() == null) {
            return;
        }
        VideoMetaEntity latest = metaMapper.selectById(meta.getVideoId());
        if (latest == null) {
            return;
        }
        latest.setStatus(VideoConstants.STATUS_FAILED);
        latest.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
        metaMapper.updateById(latest);
        log.warn("转码队列已满，元数据已标记失败: videoId={}", meta.getVideoId());
    }

    private Runnable wrapWithTaskDeadline(Runnable task) {
        long maxSeconds = videoProperties.getTask().getMaxSeconds();
        return () -> ProcessTaskDeadline.runWithDeadline(maxSeconds, task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
