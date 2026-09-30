package com.shumamall.video.config;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.config.VideoProperties;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.entity.UploadSessionDoc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 转码线程池拒绝策略单元测试（不启动 Spring）。
 */
class VideoTranscodeExecutorTest {

    private VideoTranscodeExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void scheduleSessionTranscode_rejectsWhenPoolAndQueueFull() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        VideoProperties properties = new VideoProperties();
        properties.getTranscode().setPoolSize(1);
        properties.getTranscode().setQueueCapacity(0);
        executor = new VideoTranscodeExecutor(mongoTemplate, properties);

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        executor.scheduleSessionTranscode("session-busy", () -> {
            workerStarted.countDown();
            try {
                releaseWorker.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(workerStarted.await(5, TimeUnit.SECONDS), "首个转码任务应占用唯一工作线程");

        UploadSessionDoc rejected = new UploadSessionDoc();
        rejected.setSessionId("session-reject");
        rejected.setStatus(VideoConstants.SESSION_UPLOADED);
        when(mongoTemplate.findById("session-reject", UploadSessionDoc.class)).thenReturn(rejected);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.scheduleSessionTranscode("session-reject", () -> {
                }));
        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
        assertEquals(VideoConstants.TRANSCODE_BUSY_MESSAGE, ex.getMessage());

        ArgumentCaptor<UploadSessionDoc> captor = ArgumentCaptor.forClass(UploadSessionDoc.class);
        verify(mongoTemplate).save(captor.capture());
        UploadSessionDoc saved = captor.getValue();
        assertEquals(VideoConstants.SESSION_FAILED, saved.getStatus());
        assertEquals(VideoConstants.TRANSCODE_BUSY_MESSAGE, saved.getFailReason());

        releaseWorker.countDown();
    }
}
