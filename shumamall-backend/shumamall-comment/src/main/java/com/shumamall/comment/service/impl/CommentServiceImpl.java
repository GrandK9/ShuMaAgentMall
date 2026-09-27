package com.shumamall.comment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shumamall.comment.dto.CommentCreateDTO;
import com.shumamall.comment.dto.CommentVO;
import com.shumamall.comment.dto.ReplyCreateDTO;
import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.comment.entity.CommentDocument;
import com.shumamall.comment.feign.ProductFeignClient;
import com.shumamall.comment.feign.UserFeignClient;
import com.shumamall.comment.service.CommentService;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 * <p>
 * 数据存储：MongoDB（评论文档 product_comment），点赞/举报防重用 Redis Set。
 * 用户名/头像在发表时通过 user 服务 Feign 获取快照，展示不再联动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 举报自动隐藏阈值 */
    private static final int REPORT_HIDE_THRESHOLD = 10;

    /** 状态：正常 */
    private static final int STATUS_NORMAL = 0;
    /** 状态：作者删除 */
    private static final int STATUS_DELETED = 1;
    /** 状态：管理员隐藏 */
    private static final int STATUS_HIDDEN = 2;

    /** 默认排序：最新 */
    private static final String SORT_LATEST = "latest";
    /** 排序：最热 */
    private static final String SORT_HOT = "hot";

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;

    @Override
    public PageResult<CommentVO> pageRootComments(Long productId, int page, int size, String sortBy) {
        Criteria criteria = Criteria.where("productId").is(productId)
                .and("status").is(STATUS_NORMAL)
                .and("rootId").is(null);

        Sort sort = SORT_HOT.equals(sortBy)
                ? Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt"))
                : Sort.by(Sort.Direction.DESC, "createdAt");

        return doPage(criteria, sort, page, size);
    }

    @Override
    public List<CommentVO> hotComments(int limit) {
        int max = Math.min(Math.max(limit, 1), 50);
        // 全站热门根评论：仅正常状态、仅根评论、有赞才上榜，按点赞数倒序
        Criteria criteria = Criteria.where("status").is(STATUS_NORMAL)
                .and("rootId").is(null)
                .and("likeCount").gt(0);
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "likeCount")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
                .limit(max);
        List<CommentDocument> list = mongoTemplate.find(query, CommentDocument.class);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public PageResult<CommentVO> pageReplies(Long rootId, int page, int size) {
        Criteria criteria = Criteria.where("rootId").is(rootId)
                .and("status").is(STATUS_NORMAL);
        // 回复按时间正序展示（聊天式）
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
        return doPage(criteria, sort, page, size);
    }

    @Override
    public CommentVO createComment(Long userId, CommentCreateDTO dto) {
        requireLogin(userId);

        CommentDocument doc = new CommentDocument();
        doc.setId(IdWorker.getId());
        doc.setProductId(dto.getProductId());
        doc.setContent(dto.getContent().trim());
        doc.setRating(dto.getRating());
        // 评论附带视频（可选）：前端先调 video 服务上传拿到 videoId，这里只做引用；
        // 视频二进制/HLS 分片由 video 服务托管，评论模块不持有媒体数据。
        doc.setVideoId(dto.getVideoId());
        doc.setLikeCount(0);
        doc.setReplyCount(0);
        doc.setReportCount(0);
        doc.setStatus(STATUS_NORMAL);
        doc.setCreatedAt(LocalDateTime.now());
        fillUserSnapshot(doc, userId);

        mongoTemplate.insert(doc);
        syncCommentCount(doc.getProductId(), 1);
        log.info("发表评论: commentId={}, productId={}, userId={}", doc.getId(), doc.getProductId(), userId);
        return toVO(doc);
    }

    @Override
    public CommentVO reply(Long userId, Long rootId, ReplyCreateDTO dto) {
        requireLogin(userId);

        CommentDocument root = findById(rootId);
        if (root == null || root.getRootId() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "根评论不存在");
        }

        // 可选：校验父级回复属于同一根评论，防止跨树挂载
        Long parentId = dto.getParentId();
        if (parentId != null) {
            CommentDocument parent = findById(parentId);
            if (parent == null || !rootId.equals(parent.getRootId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "父级回复不存在或不属于该根评论");
            }
        }

        CommentDocument doc = new CommentDocument();
        doc.setId(IdWorker.getId());
        doc.setProductId(root.getProductId());
        doc.setRootId(rootId);
        doc.setParentId(parentId);
        doc.setContent(dto.getContent().trim());
        doc.setLikeCount(0);
        doc.setReplyCount(0);
        doc.setReportCount(0);
        doc.setStatus(STATUS_NORMAL);
        doc.setReplyToUserId(dto.getReplyToUserId());
        doc.setReplyToUsername(dto.getReplyToUsername());
        doc.setCreatedAt(LocalDateTime.now());
        fillUserSnapshot(doc, userId);

        mongoTemplate.insert(doc);

        // 根评论回复数 +1
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(rootId)),
                new Update().inc("replyCount", 1),
                CommentDocument.class);

        syncCommentCount(doc.getProductId(), 1);
        log.info("回复评论: replyId={}, rootId={}, userId={}", doc.getId(), rootId, userId);
        return toVO(doc);
    }

    @Override
    public Integer like(Long userId, Long commentId) {
        requireLogin(userId);

        CommentDocument doc = findById(commentId);
        if (doc == null || doc.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

        String redisKey = "comment:like:" + commentId;
        Boolean added = stringRedisTemplate.opsForSet().add(redisKey, userId.toString()) > 0;
        if (!added) {
            // 已点过赞，幂等返回当前点赞数
            return doc.getLikeCount() == null ? 0 : doc.getLikeCount();
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().inc("likeCount", 1),
                CommentDocument.class);

        int newCount = (doc.getLikeCount() == null ? 0 : doc.getLikeCount()) + 1;
        log.debug("点赞评论: commentId={}, userId={}, likeCount={}", commentId, userId, newCount);
        return newCount;
    }

    @Override
    public void report(Long userId, Long commentId) {
        requireLogin(userId);

        CommentDocument doc = findById(commentId);
        if (doc == null || doc.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

        // 与点赞同一套 Redis Set 幂等：同一用户对同一评论只计一次举报。
        // 否则单人连点即可累积到自动隐藏阈值，形成刷举报（越权隐藏他人评论）。
        String redisKey = "comment:report:" + commentId;
        boolean firstReport = stringRedisTemplate.opsForSet().add(redisKey, userId.toString()) > 0;
        if (!firstReport) {
            log.debug("重复举报已忽略: commentId={}, userId={}", commentId, userId);
            return;
        }

        CommentDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().inc("reportCount", 1),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                CommentDocument.class);

        int reportCount = updated == null || updated.getReportCount() == null
                ? 0 : updated.getReportCount();
        log.info("举报评论: commentId={}, userId={}, reportCount={}", commentId, userId, reportCount);

        // 举报数达到阈值自动隐藏
        if (reportCount >= REPORT_HIDE_THRESHOLD) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(commentId)),
                    new Update().set("status", STATUS_HIDDEN),
                    CommentDocument.class);
            log.warn("评论举报达阈值自动隐藏: commentId={}, reportCount={}", commentId, reportCount);
        }
    }

    @Override
    public void delete(Long userId, Long commentId) {
        requireLogin(userId);

        CommentDocument doc = findById(commentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!userId.equals(doc.getUserId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "只能删除自己的评论");
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().set("status", STATUS_DELETED),
                CommentDocument.class);
        syncCommentCount(doc.getProductId(), -1);
        log.info("删除评论: commentId={}, userId={}", commentId, userId);
    }

    @Override
    public PageResult<CommentVO> adminPage(Long productId, Integer status, int page, int size) {
        Criteria criteria = new Criteria();
        if (productId != null) {
            criteria = criteria.and("productId").is(productId);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        return doPage(criteria, Sort.by(Sort.Direction.DESC, "createdAt"), page, size);
    }

    @Override
    public void hide(Long commentId) {
        updateStatus(commentId, STATUS_HIDDEN);
        log.info("管理员隐藏评论: commentId={}", commentId);
    }

    @Override
    public void restore(Long commentId) {
        updateStatus(commentId, STATUS_NORMAL);
        log.info("管理员恢复评论: commentId={}", commentId);
    }

    // ==================== 私有方法 ====================

    /**
     * 分页查询通用实现。
     */
    private PageResult<CommentVO> doPage(Criteria criteria, Sort sort, int page, int size) {
        long total = mongoTemplate.count(Query.query(criteria), CommentDocument.class);
        if (total == 0) {
            return new PageResult<>(page, size, 0, Collections.emptyList());
        }

        Query query = Query.query(criteria).with(sort);
        query.skip((long) (page - 1) * size).limit(size);
        List<CommentDocument> list = mongoTemplate.find(query, CommentDocument.class);

        List<CommentVO> records = list.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(page, size, total, records);
    }

    /**
     * 根据 ID 查询评论文档。
     */
    private CommentDocument findById(Long id) {
        return mongoTemplate.findById(id, CommentDocument.class);
    }

    /**
     * 更新评论状态。
     */
    private void updateStatus(Long commentId, int status) {
        CommentDocument doc = findById(commentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().set("status", status),
                CommentDocument.class);
    }

    /**
     * 校验登录态。
     */
    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
    }

    /**
     * 回写商品评论数（Feign 调 product 服务）。
     * <p>
     * 失败兜底已下沉到 {@code ProductFeignFallbackFactory}（超时/熔断/报错统一记警告日志并返回降级结果），
     * 因此此处不再需要 try/catch。
     *
     * @param productId 商品 ID
     * @param delta     增量（发评/回复 +1，删除 -1）
     */
    private void syncCommentCount(Long productId, int delta) {
        productFeignClient.updateCommentCount(productId, delta);
    }

    /**
     * 填充用户名/头像快照（Feign 调 user 服务）。
     * <p>
     * 用户服务不可用时 {@code UserFeignFallbackFactory} 返回 data 为 null 的降级结果，
     * 此时落到默认展示名「用户{userId}」，发表评论主流程不被阻断。
     */
    private void fillUserSnapshot(CommentDocument doc, Long userId) {
        doc.setUserId(userId);
        UserInfoDTO userInfo = userFeignClient.getUserInfo(userId).getData();
        if (userInfo != null) {
            doc.setUsername(StringUtils.hasText(userInfo.getNickname())
                    ? userInfo.getNickname() : userInfo.getUsername());
            doc.setAvatar(userInfo.getAvatar());
            return;
        }
        doc.setUsername("用户" + userId);
        doc.setAvatar(null);
    }

    /**
     * 文档转视图对象。
     */
    private CommentVO toVO(CommentDocument doc) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }
}
