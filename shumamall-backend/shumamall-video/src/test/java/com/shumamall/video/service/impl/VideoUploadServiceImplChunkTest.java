package com.shumamall.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dao.VideoMetaMapper;
import com.shumamall.video.dao.VideoUploadSessionMapper;
import com.shumamall.video.entity.VideoMetaEntity;
import com.shumamall.video.entity.VideoUploadSessionEntity;
import com.shumamall.video.service.UploadSessionCleanupService;
import com.shumamall.video.service.VideoProcessService;
import io.minio.ComposeObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片只有在浏览器摘要和写入后的 ETag 一致时才记入会话。
 */
@ExtendWith(MockitoExtension.class)
class VideoUploadServiceImplChunkTest {

    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    @Mock
    private VideoUploadSessionMapper uploadSessionMapper;
    @Mock
    private VideoMetaMapper metaMapper;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private VideoProcessService videoProcessService;
    @Mock
    private UploadSessionCleanupService uploadSessionCleanupService;

    private VideoUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VideoUploadServiceImpl(
                uploadSessionMapper, metaMapper, minioClient, minioConfig,
                videoProcessService, uploadSessionCleanupService);
    }

    @Test
    void putObjectArgs_不超过一片时按单次写入() throws Exception {
        PutObjectArgs small = PutObjectArgs.builder()
                .bucket("shumamall-raw")
                .object("raw/s1/chunk_0")
                .stream(new ByteArrayInputStream(new byte[3]), 3, -1)
                .build();
        assertThat(small.partCount()).isEqualTo(1);

        int full = VideoConstants.CHUNK_SIZE;
        PutObjectArgs exact = PutObjectArgs.builder()
                .bucket("shumamall-raw")
                .object("raw/s1/chunk_0")
                .stream(new ByteArrayInputStream(new byte[full]), full, -1)
                .build();
        assertThat(exact.partCount()).isEqualTo(1);
    }

    @Test
    void uploadChunk_摘要一致才记入索引() throws Exception {
        VideoUploadSessionEntity session = uploadingSession();
        when(uploadSessionMapper.selectById("s1")).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(etag("\"" + MD5_ABC + "\""));

        service.uploadChunk("s1", 0, file("abc"), MD5_ABC);

        assertThat(session.getChunks()).containsExactly(0);
        verify(uploadSessionMapper).updateById(session);
    }

    @Test
    void uploadChunk_与浏览器摘要不一致时不写入() throws Exception {
        when(uploadSessionMapper.selectById("s1")).thenReturn(uploadingSession());

        assertThatThrownBy(() -> service.uploadChunk("s1", 0, file("abc"), "ab".repeat(16)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_CHECKSUM_MISMATCH);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(uploadSessionMapper, never()).updateById(any(VideoUploadSessionEntity.class));
    }

    @Test
    void uploadChunk_写入后ETag不一致时不记索引() throws Exception {
        VideoUploadSessionEntity session = uploadingSession();
        when(uploadSessionMapper.selectById("s1")).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(etag("d".repeat(32)));

        assertThatThrownBy(() -> service.uploadChunk("s1", 0, file("abc"), MD5_ABC))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_CHECKSUM_MISMATCH);
        assertThat(session.getChunks()).isEmpty();
        verify(uploadSessionMapper, never()).updateById(any(VideoUploadSessionEntity.class));
    }

    @Test
    void completeUpload_大小与片号齐全才合并() throws Exception {
        VideoUploadSessionEntity session = uploadingSession();
        session.setFileSize(3L);
        session.getChunks().add(0);
        when(uploadSessionMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(uploadSessionMapper.selectById("s1")).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(sized(3));
        VideoMetaEntity meta = new VideoMetaEntity();
        meta.setVideoId(9L);
        when(videoProcessService.createMetaForMergedUpload("s1")).thenReturn(meta);

        var resp = service.completeUpload("s1");

        assertThat(resp.getVideoId()).isEqualTo(9L);
        verify(minioClient).composeObject(any(ComposeObjectArgs.class));
    }

    @Test
    void completeUpload_缺片或大小不符时不合并() throws Exception {
        VideoUploadSessionEntity session = uploadingSession();
        session.setFileSize(VideoConstants.CHUNK_SIZE + 10L);
        session.getChunks().add(0);
        when(uploadSessionMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(uploadSessionMapper.selectById("s1")).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(sized(VideoConstants.CHUNK_SIZE));

        assertThatThrownBy(() -> service.completeUpload("s1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_LAYOUT_MISMATCH);
        verify(minioClient, never()).composeObject(any(ComposeObjectArgs.class));
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(uploadSessionMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void completeUpload_非末片不是5MB时不合并() throws Exception {
        VideoUploadSessionEntity session = uploadingSession();
        session.setFileSize(VideoConstants.CHUNK_SIZE + 10L);
        session.getChunks().add(0);
        session.getChunks().add(1);
        when(uploadSessionMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(uploadSessionMapper.selectById("s1")).thenReturn(session);
        when(uploadSessionMapper.selectById(eq("s1"))).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(sized(100));

        assertThatThrownBy(() -> service.completeUpload("s1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_LAYOUT_MISMATCH);
        verify(minioClient, never()).composeObject(any(ComposeObjectArgs.class));
        verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
    }

    private VideoUploadSessionEntity uploadingSession() {
        VideoUploadSessionEntity session = new VideoUploadSessionEntity();
        session.setSessionId("s1");
        session.setStatus(VideoConstants.SESSION_UPLOADING);
        session.setChunks(new ArrayList<>());
        return session;
    }

    private MockMultipartFile file(String text) {
        return new MockMultipartFile("data", "chunk_0", "application/octet-stream",
                text.getBytes(StandardCharsets.UTF_8));
    }

    private ObjectWriteResponse etag(String value) {
        return new ObjectWriteResponse(Headers.of(), "shumamall-raw", null, "raw/s1/chunk_0", value, null);
    }

    private StatObjectResponse sized(long size) {
        return new StatObjectResponse(
                Headers.of(
                        "Content-Length", Long.toString(size),
                        "Last-Modified", "Wed, 21 Oct 2026 08:00:00 GMT"),
                "shumamall-raw",
                null,
                "raw/s1/chunk_0");
    }
}
