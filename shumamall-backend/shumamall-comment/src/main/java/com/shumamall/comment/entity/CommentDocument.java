package com.shumamall.comment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 评论文档（MongoDB 集合：product_comment）。
 * <p>
 * 文档模型设计：每条评论/回复是一条独立文档，
 * 通过 rootId（根评论）与 parentId（直接父级）构建嵌套回复树，
 * 无需 JOIN，天然支持嵌套回复。
 */
@Data
@Document(collection = "product_comment")
@CompoundIndexes({
        @CompoundIndex(name = "idx_product_status", def = "{'productId': 1, 'status': 1}"),
        @CompoundIndex(name = "idx_root_status", def = "{'rootId': 1, 'status': 1}")
})
public class CommentDocument {

    /** 评论 ID（雪花 ID，与全项目主键生成策略一致） */
    @Id
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 根评论 ID；回复指向所属根评论，根评论自身为 null */
    private Long rootId;

    /** 直接父评论 ID；根评论自身为 null */
    private Long parentId;

    /** 评论人用户 ID */
    private Long userId;

    /** 用户名快照（评论时写入，避免展示时频繁 Feign 调用） */
    private String username;

    /** 头像快照 */
    private String avatar;

    /** 评论内容 */
    private String content;

    /** 评分 1-5（仅根评论填写，可空） */
    private Integer rating;

    /** 评论附带视频 ID（可选，仅根评论可带；二进制/HLS 在 video 服务侧） */
    private Long videoId;

    /** 点赞数 */
    private Integer likeCount;

    /** 回复数（根评论的回复数量，点赞/展示用） */
    private Integer replyCount;

    /** 举报数 */
    private Integer reportCount;

    /** 状态 0-正常 1-作者删除 2-管理员隐藏 */
    private Integer status;

    /** 回复对象用户 ID（仅回复文档使用，@xxx 展示） */
    private Long replyToUserId;

    /** 回复对象用户名（仅回复文档使用） */
    private String replyToUsername;

    /** 创建时间 */
    @Indexed
    private LocalDateTime createdAt;
}
