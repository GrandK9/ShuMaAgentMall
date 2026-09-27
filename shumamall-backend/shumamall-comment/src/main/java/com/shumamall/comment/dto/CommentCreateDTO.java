package com.shumamall.comment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论请求 DTO。
 */
@Data
public class CommentCreateDTO {

    /** 商品 ID */
    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过 500 字")
    private String content;

    /** 评分 1-5（可选） */
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    /**
     * 评论附带视频 ID（可选）。
     * <p>
     * 前端先调 {@code POST /api/v1/video/upload} 拿到 videoId 再发表评论；
     * 雪花 ID 超出 JS 安全整数范围，前端按字符串传回，Jackson 可正常反序列化为 Long。
     */
    private Long videoId;
}
