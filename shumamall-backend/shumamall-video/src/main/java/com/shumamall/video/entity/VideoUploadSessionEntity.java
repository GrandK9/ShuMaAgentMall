package com.shumamall.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "video_upload_session", autoResultMap = true)
public class VideoUploadSessionEntity {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String hash;
    private Long uploaderId;
    private String uploaderType;
    private String sourceType;
    private String limitType;
    private String status;
    private String failReason;

    @TableField(value = "chunks_json", typeHandler = JacksonTypeHandler.class)
    private List<Integer> chunks = new ArrayList<>();

    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    private LocalDateTime createdAt;
    private LocalDateTime expireAt;
}
