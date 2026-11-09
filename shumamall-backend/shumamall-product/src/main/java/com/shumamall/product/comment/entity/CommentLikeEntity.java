package com.shumamall.product.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment_like")
public class CommentLikeEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long commentId;

    private Long userId;

    private LocalDateTime createdAt;
}
