package com.shumamall.video.config;

import com.shumamall.video.constant.VideoConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 视频服务可调参数：IO 空闲/上限、转码命令超时、异步任务总时长等。
 */
@Data
@ConfigurationProperties(prefix = "video")
public class VideoProperties {

    private Task task = new Task();

    private Io io = new Io();

    private Transcode transcode = new Transcode();

    /** 异步/同步整段处理的总时长上限（秒） */
    @Data
    public static class Task {
        private long maxSeconds = 7200;
    }

    /** MinIO HTTP 与读写阶段超时（秒） */
    @Data
    public static class Io {
        /** TCP 连接超时 */
        private int connectTimeoutSeconds = 15;
        /** 两次读之间的最大空闲（stall 检测） */
        private int readIdleTimeoutSeconds = 120;
        /** 两次写之间的最大空闲 */
        private int writeIdleTimeoutSeconds = 60;
        /** 管理端 raw 下载总时长上限（对齐大文件场景） */
        private long downloadMaxSecondsAdmin = 1800;
        /** 评论区 raw 下载总时长上限 */
        private long downloadMaxSecondsComment = 300;
        /** 单个 HLS/缩略图对象上传总时长上限 */
        private long uploadObjectMaxSeconds = 300;

        public long downloadMaxSecondsFor(String limitType) {
            return VideoConstants.UPLOADER_ADMIN.equals(limitType)
                    ? downloadMaxSecondsAdmin : downloadMaxSecondsComment;
        }
    }

    /** 转码线程池与 ffprobe/ffmpeg 命令超时（秒） */
    @Data
    public static class Transcode {
        private int poolSize = 2;
        private int queueCapacity = 5;
        private long ffprobeTimeoutSeconds = 30;
        private long hlsTimeoutSecondsAdmin = 3600;
        private long hlsTimeoutSecondsComment = 300;
        private long thumbnailTimeoutSeconds = 30;

        public long hlsTimeoutSecondsFor(String limitType) {
            return VideoConstants.UPLOADER_ADMIN.equals(limitType)
                    ? hlsTimeoutSecondsAdmin : hlsTimeoutSecondsComment;
        }
    }
}
