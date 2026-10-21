package com.shumamall.comment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 点赞关系（MongoDB 集合：comment_like）。
 * <p>
 * 一条文档表示某用户赞过某条评论。评论上的 likeCount 仍是展示用计数，
 * 谁赞过以这里为准。唯一索引让同一个人再点时插入失败，计数不会加第二次。
 */
@Data
@Document(collection = "comment_like")
@CompoundIndexes({
        @CompoundIndex(name = "uk_comment_user", def = "{'commentId': 1, 'userId': 1}", unique = true)
})
public class CommentLikeDocument {

    @Id
    private Long id;

    /** 评论 ID */
    private Long commentId;

    /** 点赞用户 ID */
    private Long userId;

    /** 点赞时间 */
    private LocalDateTime createdAt;
}
