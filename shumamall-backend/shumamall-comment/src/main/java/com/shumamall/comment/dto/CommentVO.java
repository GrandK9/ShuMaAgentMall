package com.shumamall.comment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论视图对象（前端直接渲染）。
 * <p>
 * 雪花 ID 字段（id / rootId / parentId / videoId）必须以字符串下发：
 * 19 位长整型超出 JS {@code Number.MAX_SAFE_INTEGER}（2^53-1），按 JSON number 下发时
 * 浏览器 {@code JSON.parse} 会静默丢末位精度，拿它回传即变成「评论不存在」。
 */
@Data
public class CommentVO {

    /** 评论 ID（雪花 ID，按字符串下发） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 根评论 ID（雪花 ID，仅回复文档非空；按字符串下发） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootId;

    /** 直接父评论 ID（雪花 ID，仅回复文档非空；按字符串下发） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 评论人用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 头像 */
    private String avatar;

    /** 评论内容 */
    private String content;

    /** 评分 1-5 */
    private Integer rating;

    /** 评论附带视频 ID（可选，按字符串下发） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long videoId;

    /** 点赞数 */
    private Integer likeCount;

    /** 回复数 */
    private Integer replyCount;

    /** 举报数 */
    private Integer reportCount;

    /** 状态 0-正常 1-作者删除 2-管理员隐藏 */
    private Integer status;

    /** 回复对象用户 ID */
    private Long replyToUserId;

    /** 回复对象用户名 */
    private String replyToUsername;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
