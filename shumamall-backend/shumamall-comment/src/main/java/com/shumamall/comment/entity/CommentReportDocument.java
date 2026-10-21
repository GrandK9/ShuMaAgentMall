package com.shumamall.comment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 举报关系（MongoDB 集合：comment_report）。
 * <p>
 * 记下是谁、什么时候举报的。评论上的 reportCount 仍是展示和隐藏用的计数。
 * 唯一索引保证同一用户对同一评论只记一次，连点不会把计数刷到隐藏阈值。
 */
@Data
@Document(collection = "comment_report")
@CompoundIndexes({
        @CompoundIndex(name = "uk_comment_user", def = "{'commentId': 1, 'userId': 1}", unique = true)
})
public class CommentReportDocument {

    @Id
    private Long id;

    /** 评论 ID */
    private Long commentId;

    /** 举报用户 ID */
    private Long userId;

    /** 举报时间 */
    private LocalDateTime createdAt;
}
