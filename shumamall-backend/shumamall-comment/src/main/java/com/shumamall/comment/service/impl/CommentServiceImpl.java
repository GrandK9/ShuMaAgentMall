package com.shumamall.comment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.comment.dto.CommentCreateDTO;
import com.shumamall.comment.dto.CommentVO;
import com.shumamall.comment.dto.ReplyCreateDTO;
import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.comment.entity.CommentDocument;
import com.shumamall.comment.entity.CommentLikeDocument;
import com.shumamall.comment.entity.CommentReportDocument;
import com.shumamall.comment.feign.ProductFeignClient;
import com.shumamall.comment.feign.UserFeignClient;
import com.shumamall.comment.service.CommentService;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.cache.UserBriefCache;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 * <p>
 * 数据存储：MongoDB（评论文档 product_comment，点赞 comment_like，举报 comment_report）。
 * 谁赞过、谁举报过以关系集合的唯一索引为准。Redis 只按用户挡短时间连点，可以过期。
 * 文档只存用户 ID。返回列表前按本页用户 ID 批量查询当前昵称和头像。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 举报自动隐藏阈值 */
    private static final int REPORT_HIDE_THRESHOLD = 10;

    /**
     * 点赞/举报防连点缓存。过期后同一用户再点，仍由关系集合的唯一索引决定计数动不动。
     * 按用户分开存，一条热门评论不会把所有点赞用户堆在一个键里。
     */
    private static final Duration ACTION_GUARD_TTL = Duration.ofMinutes(10);
    private static final String LIKE_GUARD_PREFIX = "comment:like:";
    private static final String REPORT_GUARD_PREFIX = "comment:report:";

    private static final ObjectMapper BRIEF_JSON = new ObjectMapper();

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
        return toViews(list);
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
        doc.setUserId(userId);

        mongoTemplate.insert(doc);
        syncCommentCount(doc.getProductId(), 1);
        log.info("发表评论: commentId={}, productId={}, userId={}", doc.getId(), doc.getProductId(), userId);
        return toView(doc);
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
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUserId(userId);

        mongoTemplate.insert(doc);

        // 根评论回复数 +1
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(rootId)),
                new Update().inc("replyCount", 1),
                CommentDocument.class);

        syncCommentCount(doc.getProductId(), 1);
        log.info("回复评论: replyId={}, rootId={}, userId={}", doc.getId(), rootId, userId);
        return toView(doc);
    }

    @Override
    public Integer like(Long userId, Long commentId) {
        requireLogin(userId);
        CommentDocument doc = requireVisible(commentId);
        String guardKey = LIKE_GUARD_PREFIX + commentId + ":" + userId;
        if (guardHit(guardKey)) {
            return likeCountOf(doc);
        }

        // 先插入关系。唯一索引冲突说明这个人已经赞过，计数不动。
        CommentLikeDocument like = new CommentLikeDocument();
        like.setId(IdWorker.getId());
        like.setCommentId(commentId);
        like.setUserId(userId);
        like.setCreatedAt(LocalDateTime.now());
        if (!insertOnce(like)) {
            rememberGuard(guardKey);
            log.debug("重复点赞已忽略: commentId={}, userId={}", commentId, userId);
            return likeCountOf(doc);
        }

        CommentDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().inc("likeCount", 1),
                FindAndModifyOptions.options().returnNew(true),
                CommentDocument.class);
        rememberGuard(guardKey);
        int newCount = updated == null ? likeCountOf(doc) + 1 : likeCountOf(updated);
        log.debug("点赞评论: commentId={}, userId={}, likeCount={}", commentId, userId, newCount);
        return newCount;
    }

    @Override
    public Integer unlike(Long userId, Long commentId) {
        requireLogin(userId);
        CommentDocument doc = requireVisible(commentId);
        String guardKey = LIKE_GUARD_PREFIX + commentId + ":" + userId;

        var removed = mongoTemplate.remove(
                Query.query(Criteria.where("commentId").is(commentId).and("userId").is(userId)),
                CommentLikeDocument.class);
        clearGuard(guardKey);
        if (removed.getDeletedCount() == 0) {
            return likeCountOf(doc);
        }

        // 只在还有赞时减，避免计数被减成负数。
        CommentDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(commentId).and("likeCount").gt(0)),
                new Update().inc("likeCount", -1),
                FindAndModifyOptions.options().returnNew(true),
                CommentDocument.class);
        int newCount = updated == null ? Math.max(likeCountOf(doc) - 1, 0) : likeCountOf(updated);
        log.debug("取消点赞: commentId={}, userId={}, likeCount={}", commentId, userId, newCount);
        return newCount;
    }

    @Override
    public void report(Long userId, Long commentId) {
        requireLogin(userId);
        CommentDocument doc = requireVisible(commentId);
        String guardKey = REPORT_GUARD_PREFIX + commentId + ":" + userId;
        if (guardHit(guardKey)) {
            hideIfThreshold(doc);
            return;
        }

        // 先记下是谁、什么时候举报的。插不进去就是同一个人重复举报，计数不加。
        CommentReportDocument report = new CommentReportDocument();
        report.setId(IdWorker.getId());
        report.setCommentId(commentId);
        report.setUserId(userId);
        report.setCreatedAt(LocalDateTime.now());
        if (!insertOnce(report)) {
            rememberGuard(guardKey);
            hideIfThreshold(doc);
            log.debug("重复举报已忽略: commentId={}, userId={}", commentId, userId);
            return;
        }

        CommentDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(commentId)),
                new Update().inc("reportCount", 1),
                FindAndModifyOptions.options().returnNew(true),
                CommentDocument.class);
        rememberGuard(guardKey);
        CommentDocument current = updated == null ? doc : updated;
        int reportCount = current.getReportCount() == null ? 0 : current.getReportCount();
        log.info("举报评论: commentId={}, userId={}, reportCount={}", commentId, userId, reportCount);
        hideIfThreshold(current);
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

        return new PageResult<>(page, size, total, toViews(list));
    }

    /**
     * 根据 ID 查询评论文档。
     */
    private CommentDocument findById(Long id) {
        return mongoTemplate.findById(id, CommentDocument.class);
    }

    /** 评论必须存在且处于正常状态，隐藏或已删除的不能再点赞、举报。 */
    private CommentDocument requireVisible(Long commentId) {
        CommentDocument doc = findById(commentId);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        return doc;
    }

    private int likeCountOf(CommentDocument doc) {
        return doc.getLikeCount() == null ? 0 : doc.getLikeCount();
    }

    /**
     * 插入关系文档。返回 false 表示唯一索引冲突，这次不是第一次。
     */
    private boolean insertOnce(Object document) {
        try {
            mongoTemplate.insert(document);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** 缓存命中表示短时间内已经确认过，不必再写 MongoDB。缓存故障时当作未命中。 */
    private boolean guardHit(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key) != null;
        } catch (Exception e) {
            log.warn("读取防连点缓存失败: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    private void rememberGuard(String key) {
        try {
            stringRedisTemplate.opsForValue().set(key, "1", ACTION_GUARD_TTL);
        } catch (Exception e) {
            log.warn("写入防连点缓存失败: key={}, err={}", key, e.getMessage());
        }
    }

    private void clearGuard(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("删除防连点缓存失败: key={}, err={}", key, e.getMessage());
        }
    }

    /** 举报数已经到阈值时把评论隐藏。重复举报也会再检查一次，避免上次计数加上了但隐藏没写上。 */
    private void hideIfThreshold(CommentDocument doc) {
        int reportCount = doc.getReportCount() == null ? 0 : doc.getReportCount();
        if (reportCount < REPORT_HIDE_THRESHOLD || doc.getStatus() == null || doc.getStatus() != STATUS_NORMAL) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(doc.getId()).and("status").is(STATUS_NORMAL)),
                new Update().set("status", STATUS_HIDDEN),
                CommentDocument.class);
        log.warn("评论举报达阈值自动隐藏: commentId={}, reportCount={}", doc.getId(), reportCount);
    }

    /** 列表只标出当前用户赞过的评论，匿名访问不查点赞关系。查失败时评论列表照常返回。 */
    private void markLiked(List<CommentVO> views) {
        Long userId = SecurityContext.getUserId();
        if (userId == null || views.isEmpty()) {
            return;
        }
        List<Long> ids = views.stream().map(CommentVO::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<CommentLikeDocument> likes = mongoTemplate.find(
                    Query.query(Criteria.where("commentId").in(ids).and("userId").is(userId)),
                    CommentLikeDocument.class);
            Set<Long> likedIds = likes.stream()
                    .map(CommentLikeDocument::getCommentId)
                    .collect(Collectors.toSet());
            for (CommentVO view : views) {
                view.setLiked(likedIds.contains(view.getId()));
            }
        } catch (Exception e) {
            log.warn("查询当前用户点赞关系失败: userId={}, err={}", userId, e.getMessage());
        }
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
     * 单条评论补上当前展示名和头像。
     */
    private CommentVO toView(CommentDocument doc) {
        return toViews(List.of(doc)).get(0);
    }

    /**
     * 这一页出现的用户 ID 先读 Redis。未命中的再向用户服务查一次并回写缓存。
     * 用户服务不可用或查无此人时，展示名降级为「用户{id}」。
     */
    private List<CommentVO> toViews(List<CommentDocument> docs) {
        List<CommentVO> views = docs.stream().map(this::toVO).collect(Collectors.toList());
        if (views.isEmpty()) {
            return views;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (CommentVO view : views) {
            if (view.getUserId() != null) {
                ids.add(view.getUserId());
            }
            if (view.getReplyToUserId() != null) {
                ids.add(view.getReplyToUserId());
            }
        }
        Map<Long, UserInfoDTO> users = loadUsers(ids);
        for (CommentVO view : views) {
            applyAuthor(view, users.get(view.getUserId()));
            if (view.getReplyToUserId() != null) {
                UserInfoDTO target = users.get(view.getReplyToUserId());
                view.setReplyToUsername(target == null
                        ? "用户" + view.getReplyToUserId()
                        : displayName(target, view.getReplyToUserId()));
            }
        }
        markLiked(views);
        return views;
    }

    private void applyAuthor(CommentVO view, UserInfoDTO user) {
        if (user == null) {
            view.setUsername(view.getUserId() == null ? "用户" : "用户" + view.getUserId());
            view.setAvatar(null);
            return;
        }
        view.setUsername(displayName(user, view.getUserId()));
        view.setAvatar(user.getAvatar());
    }

    private String displayName(UserInfoDTO user, Long userId) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "用户" + userId;
    }

    private Map<Long, UserInfoDTO> loadUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> idList = new ArrayList<>(ids);
        Map<Long, UserInfoDTO> users = new HashMap<>();
        List<Long> missing = readCachedBriefs(idList, users);
        if (missing.isEmpty()) {
            return users;
        }
        R<List<UserInfoDTO>> resp = userFeignClient.listBrief(missing);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            return users;
        }
        for (UserInfoDTO user : resp.getData()) {
            if (user == null || user.getId() == null) {
                continue;
            }
            users.put(user.getId(), user);
            writeCachedBrief(user);
        }
        return users;
    }

    /** 命中的放进 users，返回仍需向用户服务查询的 ID。缓存故障时全部视为未命中。 */
    private List<Long> readCachedBriefs(List<Long> idList, Map<Long, UserInfoDTO> users) {
        List<String> keys = idList.stream().map(UserBriefCache::key).toList();
        List<String> cached;
        try {
            cached = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("读取用户展示缓存失败: err={}", e.getMessage());
            return idList;
        }
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            String json = cached == null || i >= cached.size() ? null : cached.get(i);
            UserInfoDTO user = parseBrief(json);
            if (user == null) {
                missing.add(idList.get(i));
            } else {
                user.setId(idList.get(i));
                users.put(idList.get(i), user);
            }
        }
        return missing;
    }

    private void writeCachedBrief(UserInfoDTO user) {
        try {
            stringRedisTemplate.opsForValue().set(
                    UserBriefCache.key(user.getId()),
                    BRIEF_JSON.writeValueAsString(user),
                    Duration.ofMinutes(UserBriefCache.TTL_MINUTES));
        } catch (Exception e) {
            log.warn("写入用户展示缓存失败: userId={}, err={}", user.getId(), e.getMessage());
        }
    }

    private UserInfoDTO parseBrief(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return BRIEF_JSON.readValue(json, UserInfoDTO.class);
        } catch (Exception e) {
            return null;
        }
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
