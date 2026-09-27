package com.shumamall.video.exception;

/**
 * 视频校验/处理异常。
 * <p>
 * 用于 ffprobe 校验不通过（编码/分辨率/时长/大小）或 FFmpeg 切片失败时，
 * 携带面向用户的错误提示（如"请上传 H.264 编码的 MP4 文件"）。
 */
public class VideoValidationException extends RuntimeException {

    public VideoValidationException(String message) {
        super(message);
    }

    public VideoValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
