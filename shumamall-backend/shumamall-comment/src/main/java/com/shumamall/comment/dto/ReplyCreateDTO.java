package com.shumamall.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回复评论请求 DTO。
 */
@Data
public class ReplyCreateDTO {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过 500 字")
    private String content;

    /** 直接父评论 ID（可选；为空表示直接回复根评论） */
    private Long parentId;

    /** 回复对象用户 ID（可选，用于 @xxx 展示） */
    private Long replyToUserId;

    /** 回复对象用户名（可选，用于 @xxx 展示） */
    private String replyToUsername;
}
