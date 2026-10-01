package com.shumamall.video.config;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.config.VideoProperties;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.support.ProcessTaskDeadline;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
 * 管理端分片合并后的异步转码线程池：有界队列 + 拒绝时更新 Mongo 并抛出业务异常。
 */
@Slf4j
@Component
public class VideoTranscodeExecutor {

    private final MongoTemplate mongoTemplate;
    private final VideoProperties videoProperties;
    private final ThreadPoolExecutor executor;

    public VideoTranscodeExecutor(MongoTemplate mongoTemplate, VideoProperties videoProperties) {
        this.mongoTemplate = mongoTemplate;
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
    public void scheduleRetranscode(VideoMetaDoc meta, Runnable task) {
        try {
            executor.execute(wrapWithTaskDeadline(task));
        } catch (RejectedExecutionException ex) {
            markMetaTranscodeBusy(meta);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, VideoConstants.TRANSCODE_BUSY_MESSAGE);
        }
    }

    private void markSessionTranscodeBusy(String sessionId) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session != null) {
            session.setStatus(VideoConstants.SESSION_FAILED);
            session.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
            mongoTemplate.save(session);
        }
        VideoMetaDoc meta = mongoTemplate.findOne(
                new Query(Criteria.where("sessionId").is(sessionId)), VideoMetaDoc.class);
        if (meta != null) {
            meta.setStatus(VideoConstants.STATUS_FAILED);
            meta.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
            mongoTemplate.save(meta);
        }
        log.warn("转码队列已满，已标记失败: sessionId={}", sessionId);
    }

    private void markMetaTranscodeBusy(VideoMetaDoc meta) {
        if (meta == null || meta.getVideoId() == null) {
            return;
        }
        VideoMetaDoc latest = mongoTemplate.findById(meta.getVideoId(), VideoMetaDoc.class);
        if (latest == null) {
            return;
        }
        latest.setStatus(VideoConstants.STATUS_FAILED);
        latest.setFailReason(VideoConstants.TRANSCODE_BUSY_MESSAGE);
        mongoTemplate.save(latest);
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
