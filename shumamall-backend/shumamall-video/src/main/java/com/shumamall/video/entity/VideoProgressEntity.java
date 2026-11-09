package com.shumamall.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_progress")
public class VideoProgressEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Long videoId;
    private Double position;
    private LocalDateTime updatedAt;
}
