package com.shumamall.video.constant;

/**
 * 视频服务常量。
 * <p>
 * 集中管理 MinIO bucket 名、上传/处理状态机、来源类型与校验上限，
 * 避免魔法字符串/数字散落各处。
 */
public final class VideoConstants {

    private VideoConstants() {
    }

    // ==================== MinIO bucket ====================

    /** 原始视频存储桶（分片/直传的源文件） */
    public static final String BUCKET_RAW = "shumamall-raw";

    /** HLS 切片存储桶（.m3u8 + .ts） */
    public static final String BUCKET_HLS = "video-hls";

    /** 视频缩略图存储桶 */
    public static final String BUCKET_THUMB = "video-thumb";

    // ==================== 上传会话状态 ====================

    /** 分片上传中 */
    public static final String SESSION_UPLOADING = "uploading";

    /** 分片已合并，待后台处理 */
    public static final String SESSION_UPLOADED = "uploaded";

    /** 会话处理完成（含失败），videoId 已回填 */
    public static final String SESSION_PROCESSED = "processed";

    // ==================== 视频元数据状态 ====================

    /** 上传中（尚未进入校验） */
    public static final String STATUS_UPLOADING = "uploading";

    /** ffprobe 校验中 */
    public static final String STATUS_VALIDATING = "validating";

    /** 校验 + HLS 切片完成，可播放 */
    public static final String STATUS_TRANSCODED = "transcoded";

    /** 校验/切片失败 */
    public static final String STATUS_FAILED = "failed";

    // ==================== 上传者/来源类型 ====================

    /** 管理端上传 */
    public static final String UPLOADER_ADMIN = "admin";

    /** 用户端（评论区）上传 */
    public static final String UPLOADER_USER = "user";

    /** PC 浏览器（ffmpeg.wasm 客户端转码后上传） */
    public static final String SOURCE_BROWSER = "browser";

    /** 微信小程序（压缩后原始上传） */
    public static final String SOURCE_MINIAPP = "miniapp";

    /** 第三方 API（服务端完整管线兜底） */
    public static final String SOURCE_API = "api";

    // ==================== 校验上限（与架构文档一致） ====================

    /** 评论区视频限 60 秒 */
    public static final int MAX_DURATION_COMMENT = 60;

    /** 管理端视频限 30 分钟 */
    public static final int MAX_DURATION_ADMIN = 1800;

    /** 评论区视频限 100MB */
    public static final long MAX_SIZE_COMMENT = 100L * 1024 * 1024;

    /** 管理端视频限 2GB */
    public static final long MAX_SIZE_ADMIN = 2L * 1024 * 1024 * 1024;

    /** 分辨率长边上限 */
    public static final int MAX_RESOLUTION_LONG_SIDE = 4096;

    /** 分辨率短边上限 */
    public static final int MAX_RESOLUTION_SHORT_SIDE = 2160;

    /** 允许的视频编码 */
    public static final java.util.List<String> ALLOWED_VIDEO_CODECS = java.util.List.of("h264", "hevc");

    /** 允许的音频编码（无音轨时放行） */
    public static final java.util.List<String> ALLOWED_AUDIO_CODECS = java.util.List.of("aac", "mp3");

    // ==================== 其他 ====================

    /** 单分片大小（5MB） */
    public static final int CHUNK_SIZE = 5 * 1024 * 1024;

    /** 上传会话 TTL（小时），MongoDB TTL 索引自动过期 */
    public static final long SESSION_TTL_HOURS = 24;

    /** 播放签名 URL 有效期（秒，1 小时） */
    public static final int PLAY_URL_EXPIRY_SECONDS = 3600;
}
