package com.shumamall.video.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.service.UploadSessionCleanupService;
import com.shumamall.video.service.VideoProcessService;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片只有在浏览器摘要和写入后的 ETag 一致时才记入会话。
 */
@ExtendWith(MockitoExtension.class)
class VideoUploadServiceImplChunkTest {

    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    @Mock
    private MongoTemplate mongoTemplate;
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
                mongoTemplate, minioClient, minioConfig, videoProcessService, uploadSessionCleanupService);
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
        UploadSessionDoc session = uploadingSession();
        when(mongoTemplate.findById("s1", UploadSessionDoc.class)).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(etag("\"" + MD5_ABC + "\""));

        service.uploadChunk("s1", 0, file("abc"), MD5_ABC);

        assertThat(session.getChunks()).containsExactly(0);
        verify(mongoTemplate).save(session);
    }

    @Test
    void uploadChunk_与浏览器摘要不一致时不写入() throws Exception {
        when(mongoTemplate.findById("s1", UploadSessionDoc.class)).thenReturn(uploadingSession());

        assertThatThrownBy(() -> service.uploadChunk("s1", 0, file("abc"), "ab".repeat(16)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_CHECKSUM_MISMATCH);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(mongoTemplate, never()).save(any());
    }

    @Test
    void uploadChunk_写入后ETag不一致时不记索引() throws Exception {
        UploadSessionDoc session = uploadingSession();
        when(mongoTemplate.findById("s1", UploadSessionDoc.class)).thenReturn(session);
        when(minioConfig.getRawBucket()).thenReturn("shumamall-raw");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(etag("d".repeat(32)));

        assertThatThrownBy(() -> service.uploadChunk("s1", 0, file("abc"), MD5_ABC))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VideoUploadServiceImpl.CHUNK_CHECKSUM_MISMATCH);
        assertThat(session.getChunks()).isEmpty();
        verify(mongoTemplate, never()).save(any());
    }

    private UploadSessionDoc uploadingSession() {
        UploadSessionDoc session = new UploadSessionDoc();
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
}
