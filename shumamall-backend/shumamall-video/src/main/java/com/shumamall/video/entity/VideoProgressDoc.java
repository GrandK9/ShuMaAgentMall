package com.shumamall.video.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 播放进度文档（MongoDB 集合：video_progress）。
 * <p>
 * 断点续播：前端每 10 秒上报进度 → 高频写入 Redis；
 * 定时任务把内存累积的进度批量落库到本集合，读取时 Redis 优先、Mongo 兜底。
 */
@Data
@Document(collection = "video_progress")
public class VideoProgressDoc {

    /** 复合主键 userId:videoId */
    @Id
    private String id;

    /** 用户 ID */
    @Indexed
    private Long userId;

    /** 视频 ID */
    @Indexed
    private Long videoId;

    /** 播放位置（秒） */
    private Double position;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
