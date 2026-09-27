package com.shumamall.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 视频管理端分页查询参数。
 */
@Data
public class VideoPageQuery {

    /** 页码 */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer size = 20;

    /** 处理状态筛选（可选） */
    private String status;

    /** 上传者类型筛选（可选） */
    private String uploaderType;
}
