package com.shumamall.product.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_comment")
public class ProductCommentEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long productId;

    private Long rootId;

    private Long parentId;

    private Long userId;

    private String content;

    private Integer rating;

    private Long videoId;

    private Integer likeCount;

    private Integer replyCount;

    private Integer reportCount;

    private Integer status;

    private Long replyToUserId;

    private LocalDateTime createdAt;
}
