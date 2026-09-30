package com.shumamall.video.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.video.config.MinioConfig;
import com.shumamall.video.config.VideoProperties;
import com.shumamall.video.config.VideoTranscodeExecutor;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dto.UploadRespDTO;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.entity.VideoMetaDoc;
import com.shumamall.video.exception.VideoValidationException;
import com.shumamall.video.service.VideoProcessService;
import com.shumamall.video.support.ProcessTaskDeadline;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 视频处理服务实现。
 * <p>
 * 核心流程（与架构文档一致）：
 * <pre>
 * MinIO raw → 下载临时文件 → ffprobe 校验（编码/分辨率/时长/大小）
 *   → FFmpeg HLS 切片（libx264 + aac，6s/片）→ 上传 video-hls → 更新 MongoDB 元数据
 * </pre>
 * 校验不通过不返回 415（异步场景），而是把失败原因写入元数据，由前端轮询提示用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessServiceImpl implements VideoProcessService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final MongoTemplate mongoTemplate;
    private final VideoTranscodeExecutor videoTranscodeExecutor;
    private final VideoProperties videoProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${video.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** m3u8 播放清单中分片条目匹配正则：#EXTINF:duration, 换行 filename */
    private static final Pattern EXTINF_PATTERN = Pattern.compile(
            "#EXTINF:([0-9.]+),[^\\r\\n]*\\r?\\n([^\\r\\n]+)");

    @Override
    public void processSession(String sessionId) {
        videoTranscodeExecutor.scheduleSessionTranscode(sessionId, () -> doProcessSession(sessionId));
    }

    @Override
    public UploadRespDTO processFile(Long uploaderId, String uploaderType, String sourceType, MultipartFile file) {
        String limitType = resolveLimitType(uploaderType);
        String rawObject = "raw/" + IdWorker.getId() + "/source.mp4";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getRawBucket())
                    .object(rawObject)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("原始视频上传 MinIO 失败: {}", e.getMessage());
            throw new VideoValidationException("视频上传失败，请重试");
        }

        // 创建元数据并同步执行校验 + 切片（评论区小文件，等待结果返回）
        VideoMetaDoc meta = buildMeta(uploaderId, uploaderType, sourceType, limitType,
                rawObject, file.getSize(), null);
        mongoTemplate.insert(meta);

        long taskMaxSeconds = videoProperties.getTask().getMaxSeconds();
        ProcessTaskDeadline.runWithDeadline(taskMaxSeconds, () -> {
            File tempFile = downloadToTemp(minioConfig.getRawBucket(), rawObject, limitType);
            try {
                runProcess(meta, tempFile);
            } finally {
                deleteQuietly(tempFile);
            }
        });
        return new UploadRespDTO(meta.getVideoId(), meta.getStatus(),
                meta.getPlaylistUrl(), meta.getFailReason());
    }

    @Override
    public void reTranscode(VideoMetaDoc meta) {
        videoTranscodeExecutor.scheduleRetranscode(meta, () -> {
            meta.setStatus(VideoConstants.STATUS_VALIDATING);
            meta.setFailReason(null);
            meta.setHlsSegments(new ArrayList<>());
            mongoTemplate.save(meta);

            File tempFile = downloadToTemp(minioConfig.getRawBucket(), meta.getRawObject(), meta.getLimitType());
            try {
                runProcess(meta, tempFile);
            } finally {
                deleteQuietly(tempFile);
            }
        });
    }

    // ==================== 私有方法 ====================

    /**
     * 异步处理分片上传会话。
     *
     * @param sessionId 会话 ID
     */
    private void doProcessSession(String sessionId) {
        UploadSessionDoc session = mongoTemplate.findById(sessionId, UploadSessionDoc.class);
        if (session == null) {
            log.warn("处理分片会话失败：会话不存在 sessionId={}", sessionId);
            return;
        }
        String mergedObject = "raw/" + sessionId + "/merged.mp4";
        String limitType = session.getLimitType();
        VideoMetaDoc meta = buildMeta(session.getUploaderId(), session.getUploaderType(),
                session.getSourceType(), limitType, mergedObject, session.getFileSize(), sessionId);

        try {
            mongoTemplate.insert(meta);
        } catch (Exception e) {
            log.error("创建视频元数据失败: sessionId={}", sessionId, e);
            return;
        }
        // 回填会话 videoId 并标记处理完成
        session.setVideoId(meta.getVideoId());
        session.setStatus(VideoConstants.SESSION_PROCESSED);
        mongoTemplate.save(session);

        File tempFile = downloadToTemp(minioConfig.getRawBucket(), mergedObject, limitType);
        try {
            runProcess(meta, tempFile);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * 构建视频元数据（初始状态 validating）。
     */
    private VideoMetaDoc buildMeta(Long uploaderId, String uploaderType, String sourceType,
                                   String limitType, String rawObject, Long fileSize, String sessionId) {
        VideoMetaDoc meta = new VideoMetaDoc();
        meta.setVideoId(IdWorker.getId());
        meta.setUploaderId(uploaderId);
        meta.setUploaderType(uploaderType);
        meta.setSourceType(StringUtils.hasText(sourceType) ? sourceType : VideoConstants.SOURCE_BROWSER);
        meta.setLimitType(limitType);
        meta.setStatus(VideoConstants.STATUS_VALIDATING);
        meta.setRawObject(rawObject);
        meta.setFileSize(fileSize);
        meta.setSessionId(sessionId);
        meta.setUploadedAt(LocalDateTime.now());
        return meta;
    }

    /**
     * 执行校验 + 切片，结果回写元数据（transcoded / failed）。
     *
     * @param meta   视频元数据
     * @param source 本地临时源文件
     */
    private void runProcess(VideoMetaDoc meta, File source) {
        ProcessTaskDeadline.check();
        try {
            ProbeResult probe = probe(source);
            validate(probe, meta.getLimitType(), meta.getFileSize());

            meta.setFormat(probe.getVideoCodec());
            meta.setAudioCodec(probe.getAudioCodec());
            meta.setDuration(probe.getDuration());
            meta.setWidth(probe.getWidth());
            meta.setHeight(probe.getHeight());

            List<VideoMetaDoc.HlsSegment> segments = transcode(source, meta.getVideoId(), meta.getLimitType());
            meta.setHlsSegments(segments);
            meta.setPlaylistUrl("hls/" + meta.getVideoId() + "/index.m3u8");
            // 缩略图生成失败不阻塞主流程，仅告警（可播放优先）
            generateThumbnail(meta, source, meta.getLimitType());
            meta.setStatus(VideoConstants.STATUS_TRANSCODED);
            meta.setTranscodedAt(LocalDateTime.now());
            log.info("视频校验 + HLS 切片完成: videoId={}, duration={}s, segments={}",
                    meta.getVideoId(), meta.getDuration(), segments.size());
        } catch (VideoValidationException e) {
            log.warn("视频校验不通过: videoId={}, reason={}", meta.getVideoId(), e.getMessage());
            meta.setStatus(VideoConstants.STATUS_FAILED);
            meta.setFailReason(e.getMessage());
        } catch (Exception e) {
            log.error("视频处理异常: videoId={}", meta.getVideoId(), e);
            meta.setStatus(VideoConstants.STATUS_FAILED);
            meta.setFailReason("系统处理失败: " + e.getMessage());
        }
        mongoTemplate.save(meta);
    }

    /**
     * ffprobe 探测视频格式。
     *
     * @param file 视频文件
     * @return 探测结果
     */
    private ProbeResult probe(File file) {
        List<String> cmd = List.of(ffprobePath, "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", file.getAbsolutePath());
        ExecResult result = exec(cmd, videoProperties.getTranscode().getFfprobeTimeoutSeconds());
        if (result.getExitCode() != 0) {
            throw new VideoValidationException("无法读取视频信息（ffprobe 失败）");
        }
        try {
            JsonNode root = objectMapper.readTree(result.getOutput());
            JsonNode streams = root.path("streams");
            ProbeResult probe = new ProbeResult();
            for (JsonNode s : streams) {
                String codecType = s.path("codec_type").asText();
                if ("video".equals(codecType)) {
                    probe.setVideoCodec(s.path("codec_name").asText());
                    probe.setWidth(s.path("width").asInt(0));
                    probe.setHeight(s.path("height").asInt(0));
                    if (s.has("duration")) {
                        probe.setDuration(s.path("duration").asDouble());
                    }
                } else if ("audio".equals(codecType)) {
                    probe.setAudioCodec(s.path("codec_name").asText());
                }
            }
            if (probe.getDuration() == null || probe.getDuration() == 0) {
                probe.setDuration(root.path("format").path("duration").asDouble(0));
            }
            return probe;
        } catch (Exception e) {
            throw new VideoValidationException("视频信息解析失败", e);
        }
    }

    /**
     * 校验编码/分辨率/时长/大小。
     *
     * @param probe     探测结果
     * @param limitType 校验档位 comment / admin
     * @param fileSize  文件大小（字节）
     */
    private void validate(ProbeResult probe, String limitType, Long fileSize) {
        if (!StringUtils.hasText(probe.getVideoCodec())) {
            throw new VideoValidationException("未检测到视频流，请上传有效的视频文件");
        }
        if (!VideoConstants.ALLOWED_VIDEO_CODECS.contains(probe.getVideoCodec())) {
            throw new VideoValidationException("请上传 H.264 编码的 MP4 文件（当前编码: " + probe.getVideoCodec() + "）");
        }
        if (StringUtils.hasText(probe.getAudioCodec())
                && !VideoConstants.ALLOWED_AUDIO_CODECS.contains(probe.getAudioCodec())) {
            throw new VideoValidationException("音频编码需为 AAC 或 MP3（当前: " + probe.getAudioCodec() + "）");
        }

        long longSide = Math.max(probe.getWidth(), probe.getHeight());
        long shortSide = Math.min(probe.getWidth(), probe.getHeight());
        if (longSide > VideoConstants.MAX_RESOLUTION_LONG_SIDE
                || shortSide > VideoConstants.MAX_RESOLUTION_SHORT_SIDE) {
            throw new VideoValidationException("分辨率超限（长边 ≤ " + VideoConstants.MAX_RESOLUTION_LONG_SIDE
                    + "，短边 ≤ " + VideoConstants.MAX_RESOLUTION_SHORT_SIDE + "）");
        }

        boolean admin = VideoConstants.UPLOADER_ADMIN.equals(limitType);
        double maxDuration = admin ? VideoConstants.MAX_DURATION_ADMIN : VideoConstants.MAX_DURATION_COMMENT;
        long maxSize = admin ? VideoConstants.MAX_SIZE_ADMIN : VideoConstants.MAX_SIZE_COMMENT;
        if (probe.getDuration() > maxDuration) {
            throw new VideoValidationException("时长超限（" + (admin ? "管理端最长 30 分钟" : "评论区最长 60 秒") + "）");
        }
        if (fileSize != null && fileSize > maxSize) {
            throw new VideoValidationException("文件过大（" + (admin ? "管理端上限 2GB" : "评论区上限 100MB") + "）");
        }
    }

    /**
     * FFmpeg HLS 切片并上传 MinIO，返回分片列表。
     *
     * @param source  源文件
     * @param videoId 视频 ID
     * @return 分片列表
     */
    private List<VideoMetaDoc.HlsSegment> transcode(File source, Long videoId, String limitType) throws Exception {
        Path outDir = Files.createTempDirectory("hls-" + videoId);
        Path playlist = outDir.resolve("index.m3u8");

        List<String> cmd = List.of(ffmpegPath, "-y", "-i", source.getAbsolutePath(),
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k",
                "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", outDir.resolve("segment_%03d.ts").toString(),
                playlist.toString());
        ExecResult result = exec(cmd, videoProperties.getTranscode().hlsTimeoutSecondsFor(limitType));
        if (result.getExitCode() != 0) {
            throw new VideoValidationException("HLS 切片失败: " + truncate(result.getOutput(), 200));
        }

        // 上传全部输出文件（m3u8 + ts）到 video-hls bucket
        try (Stream<Path> files = Files.list(outDir)) {
            files.forEach(p -> {
                String object = "hls/" + videoId + "/" + p.getFileName().toString();
                uploadFileToMinio(minioConfig.getHlsBucket(), object, p.toFile());
            });
        }

        List<VideoMetaDoc.HlsSegment> segments = parsePlaylist(playlist, videoId);
        log.debug("HLS 切片上传完成: videoId={}, 分片数={}", videoId, segments.size());
        return segments;
    }

    /**
     * 生成视频缩略图（取中间帧）并上传 MinIO thumb bucket。
     * <p>
     * 缩略图路径固定为 {@code thumb/{videoId}.jpg}，写入元数据 thumbnailUrl。
     * 失败仅记录告警，不抛出异常、不阻塞 HLS 播放主流程。
     *
     * @param meta   视频元数据
     * @param source 本地源文件
     */
    private void generateThumbnail(VideoMetaDoc meta, File source, String limitType) {
        try {
            Path thumbDir = Files.createTempDirectory("thumb-" + meta.getVideoId());
            Path thumbFile = thumbDir.resolve("thumb.jpg");
            try {
                double duration = meta.getDuration() == null ? 0 : meta.getDuration();
                // 取中间帧，时长未知时兜底第 0.5 秒；-ss 前置走 seek 更快
                double seek = duration > 0 ? Math.max(0.5, duration * 0.5) : 0.5;
                List<String> cmd = List.of(ffmpegPath, "-y", "-ss", String.valueOf(seek),
                        "-i", source.getAbsolutePath(), "-vframes", "1", "-q:v", "2",
                        thumbFile.toString());
                ExecResult result = exec(cmd, videoProperties.getTranscode().getThumbnailTimeoutSeconds());
                if (result.getExitCode() != 0) {
                    throw new VideoValidationException("缩略图生成失败: " + truncate(result.getOutput(), 200));
                }
                String object = "thumb/" + meta.getVideoId() + ".jpg";
                uploadFileToMinio(minioConfig.getThumbBucket(), object, thumbFile.toFile());
                meta.setThumbnailUrl(object);
                log.info("视频缩略图生成完成: videoId={}", meta.getVideoId());
            } finally {
                try {
                    Files.deleteIfExists(thumbFile);
                    Files.deleteIfExists(thumbDir);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        } catch (Exception e) {
            log.warn("视频缩略图生成失败，跳过: videoId={}, error={}", meta.getVideoId(), e.getMessage());
        }
    }

    /**
     * 解析 m3u8 播放清单，提取分片序号/时长/对象路径。
     * <p>
     * m3u8 内的分片名是相对路径（如 {@code segment_000.ts}），而 MinIO 中的
     * 实际对象路径为 {@code hls/{videoId}/segment_000.ts}，此处必须补全 videoId；
     * 否则播放接口拿该路径去签名会指向不存在的对象，分片一律 404。
     *
     * @param playlist m3u8 文件
     * @param videoId  视频 ID
     * @return 分片列表
     */
    private List<VideoMetaDoc.HlsSegment> parsePlaylist(Path playlist, Long videoId) throws Exception {
        List<VideoMetaDoc.HlsSegment> segments = new ArrayList<>();
        String content = Files.readString(playlist, StandardCharsets.UTF_8);
        Matcher matcher = EXTINF_PATTERN.matcher(content);
        int index = 0;
        while (matcher.find()) {
            double duration = Double.parseDouble(matcher.group(1));
            String segmentFile = matcher.group(2).trim();
            segments.add(new VideoMetaDoc.HlsSegment(index++, duration, "hls/" + videoId + "/" + segmentFile));
        }
        return segments;
    }

    /**
     * 从 MinIO 下载对象到本地临时文件。
     *
     * @param bucket bucket 名称
     * @param object 对象路径
     * @return 临时文件
     */
    private File downloadToTemp(String bucket, String object, String limitType) {
        long downloadMaxSeconds = videoProperties.getIo().downloadMaxSecondsFor(limitType);
        try {
            Path tempFile = Files.createTempFile("video-", ".mp4");
            long downloadDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(downloadMaxSeconds);
            byte[] buffer = new byte[8192];
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(object).build());
                 OutputStream out = Files.newOutputStream(tempFile)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    ProcessTaskDeadline.check();
                    if (System.nanoTime() > downloadDeadlineNanos) {
                        throw new VideoValidationException("原始视频下载超时");
                    }
                    out.write(buffer, 0, read);
                }
            }
            return tempFile.toFile();
        } catch (VideoValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 下载失败: bucket={}, object={}, error={}", bucket, object, e.getMessage());
            throw new VideoValidationException("原始视频读取失败");
        }
    }

    /**
     * 上传本地文件到 MinIO。
     *
     * @param bucket bucket 名称
     * @param object 对象路径
     * @param file   本地文件
     */
    private void uploadFileToMinio(String bucket, String object, File file) {
        try {
            long maxSeconds = videoProperties.getIo().getUploadObjectMaxSeconds();
            try (InputStream in = openBoundedUploadStream(file.toPath(), maxSeconds)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(object)
                        .stream(in, file.length(), -1)
                        .build());
            }
        } catch (VideoValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new VideoValidationException("HLS 分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传流：结合任务总 deadline 与单对象上传上限。
     */
    private InputStream openBoundedUploadStream(Path path, long objectMaxSeconds) throws IOException {
        long objectDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(objectMaxSeconds);
        InputStream raw = Files.newInputStream(path);
        return new FilterInputStream(raw) {
            private void guard() {
                ProcessTaskDeadline.check();
                if (System.nanoTime() > objectDeadlineNanos) {
                    throw new VideoValidationException("对象上传超时");
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                guard();
                return super.read(b, off, len);
            }

            @Override
            public int read() throws IOException {
                guard();
                return super.read();
            }
        };
    }

    /**
     * 执行外部命令（ffprobe / ffmpeg），带超时控制。
     * <p>
     * 输出重定向到临时文件而非管道：若用管道，子进程输出超过管道缓冲区
     * （Windows 约 4KB）会阻塞在写操作上，而本方法在进程退出前不读取管道，
     * 双方互等造成死锁，最终误报"视频处理超时"。ffprobe 的 JSON 输出
     * （含音轨时 4KB 以上）与 ffmpeg 的转码日志都超过该阈值。
     *
     * @param cmd            命令与参数
     * @param timeoutSeconds 超时秒数
     * @return 执行结果
     */
    private ExecResult exec(List<String> cmd, long timeoutSeconds) {
        ProcessTaskDeadline.check();
        Path outFile = null;
        try {
            outFile = Files.createTempFile("exec-", ".log");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outFile.toFile());
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new VideoValidationException("视频处理超时");
            }
            String output = Files.readString(outFile, StandardCharsets.UTF_8);
            return new ExecResult(process.exitValue(), output);
        } catch (VideoValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("外部命令执行失败: cmd={}", cmd, e);
            throw new VideoValidationException("视频处理命令执行失败: " + e.getMessage());
        } finally {
            if (outFile != null) {
                try {
                    Files.deleteIfExists(outFile);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    /**
     * 解析校验档位：admin → admin 上限，其余 → comment 上限。
     *
     * @param uploaderType 上传者类型
     * @return 校验档位
     */
    private String resolveLimitType(String uploaderType) {
        return VideoConstants.UPLOADER_ADMIN.equals(uploaderType)
                ? VideoConstants.UPLOADER_ADMIN : VideoConstants.UPLOADER_USER;
    }

    /**
     * 截断错误输出，避免日志/提示过长。
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 静默删除临时文件。
     */
    private void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
            // 临时文件清理失败不影响主流程
        }
    }

    /**
     * ffprobe 探测结果。
     */
    @Data
    private static class ProbeResult {

        /** 视频编码 */
        private String videoCodec;

        /** 音频编码（可为空） */
        private String audioCodec;

        /** 宽度 */
        private Integer width;

        /** 高度 */
        private Integer height;

        /** 时长（秒） */
        private Double duration;
    }

    /**
     * 外部命令执行结果。
     */
    @Data
    @RequiredArgsConstructor
    private static class ExecResult {

        /** 退出码 */
        private final int exitCode;

        /** 输出内容（stdout + stderr 合并） */
        private final String output;
    }
}
