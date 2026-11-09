package com.shumamall.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 视频服务启动类。
 * <p>
 * 负责视频上传（分片/直传）、ffprobe 格式校验、FFmpeg HLS 切片、
 * 播放签名 URL 与断点续播。元数据存 MySQL，二进制存 MinIO。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.shumamall.video",
        "com.shumamall.common"
})
@MapperScan("com.shumamall.video.dao")
public class VideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoApplication.class, args);
    }
}
