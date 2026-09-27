package com.shumamall.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 视频服务启动类。
 * <p>
 * 负责视频上传（分片/直传）、ffprobe 格式校验、FFmpeg HLS 切片、
 * 播放签名 URL 与断点续播。元数据存 MongoDB，二进制存 MinIO。
 * <p>
 * 本服务不连 MySQL，排除 {@link DataSourceAutoConfiguration}：
 * shumamall-common 传递引入了 mybatis-plus starter（含 JDBC 驱动），
 * 不禁用会因缺少 datasource url 导致启动失败。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.shumamall", exclude = DataSourceAutoConfiguration.class)
public class VideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoApplication.class, args);
    }
}
