package com.shumamall.product.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.product.comment.dao.CommentLikeMapper;
import com.shumamall.product.comment.dao.CommentReportMapper;
import com.shumamall.product.comment.dao.ProductCommentMapper;
import com.shumamall.product.comment.dto.CommentCreateDTO;
import com.shumamall.product.comment.dto.CommentVO;
import com.shumamall.product.comment.dto.ReplyCreateDTO;
import com.shumamall.product.comment.dto.UserInfoDTO;
import com.shumamall.product.comment.entity.CommentLikeEntity;
import com.shumamall.product.comment.entity.CommentReportEntity;
import com.shumamall.product.comment.entity.ProductCommentEntity;
import com.shumamall.product.comment.feign.UserFeignClient;
import com.shumamall.product.comment.spi.ProductCommentCounter;
import com.shumamall.product.comment.service.CommentService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final int REPORT_HIDE_THRESHOLD = 10;
    private static final Duration ACTION_GUARD_TTL = Duration.ofMinutes(10);
    private static final String LIKE_GUARD_PREFIX = "comment:like:";
    private static final String REPORT_GUARD_PREFIX = "comment:report:";
    private static final ObjectMapper BRIEF_JSON = new ObjectMapper();
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DELETED = 1;
    private static final int STATUS_HIDDEN = 2;
    private static final String SORT_HOT = "hot";

    private final ProductCommentMapper productCommentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final CommentReportMapper commentReportMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;
    private final ProductCommentCounter productCommentCounter;

    @Override
    public PageResult<CommentVO> pageRootComments(Long productId, int page, int size, String sortBy) {
        LambdaQueryWrapper<ProductCommentEntity> w = new LambdaQueryWrapper<>();
        w.eq(ProductCommentEntity::getProductId, productId)
                .eq(ProductCommentEntity::getStatus, STATUS_NORMAL)
                .isNull(ProductCommentEntity::getRootId);
        if (SORT_HOT.equals(sortBy)) {
            w.orderByDesc(ProductCommentEntity::getLikeCount)
                    .orderByDesc(ProductCommentEntity::getCreatedAt);
        } else {
            w.orderByDesc(ProductCommentEntity::getCreatedAt);
        }
        return doPage(w, page, size);
    }

    @Override
    public List<CommentVO> hotComments(int limit) {
        int max = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<ProductCommentEntity> w = new LambdaQueryWrapper<>();
        w.eq(ProductCommentEntity::getStatus, STATUS_NORMAL)
                .isNull(ProductCommentEntity::getRootId)
                .gt(ProductCommentEntity::getLikeCount, 0)
                .orderByDesc(ProductCommentEntity::getLikeCount)
                .orderByDesc(ProductCommentEntity::getCreatedAt)
                .last("LIMIT " + max);
        List<ProductCommentEntity> list = productCommentMapper.selectList(w);
        return toViews(list);
    }

    @Override
    public PageResult<CommentVO> pageReplies(Long rootId, int page, int size) {
        LambdaQueryWrapper<ProductCommentEntity> w = new LambdaQueryWrapper<>();
        w.eq(ProductCommentEntity::getRootId, rootId)
                .eq(ProductCommentEntity::getStatus, STATUS_NORMAL)
                .orderByAsc(ProductCommentEntity::getCreatedAt);
        return doPage(w, page, size);
    }

    @Override
    public CommentVO createComment(Long userId, CommentCreateDTO dto) {
        requireLogin(userId);
        ProductCommentEntity row = new ProductCommentEntity();
        row.setId(IdWorker.getId());
        row.setProductId(dto.getProductId());
        row.setContent(dto.getContent().trim());
        row.setRating(dto.getRating());
        row.setVideoId(dto.getVideoId());
        row.setLikeCount(0);
        row.setReplyCount(0);
        row.setReportCount(0);
        row.setStatus(STATUS_NORMAL);
        row.setCreatedAt(LocalDateTime.now());
        row.setUserId(userId);
        productCommentMapper.insert(row);
        syncCommentCount(row.getProductId(), 1);
        log.info("发表评论: commentId={}, productId={}, userId={}", row.getId(), row.getProductId(), userId);
        return toView(row);
    }

    @Override
    public CommentVO reply(Long userId, Long rootId, ReplyCreateDTO dto) {
        requireLogin(userId);
        ProductCommentEntity root = findById(rootId);
        if (root == null || root.getRootId() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "根评论不存在");
        }
        Long parentId = dto.getParentId();
        if (parentId != null) {
            ProductCommentEntity parent = findById(parentId);
            if (parent == null || !rootId.equals(parent.getRootId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "父级回复不存在或不属于该根评论");
            }
        }
        ProductCommentEntity row = new ProductCommentEntity();
        row.setId(IdWorker.getId());
        row.setProductId(root.getProductId());
        row.setRootId(rootId);
        row.setParentId(parentId);
        row.setContent(dto.getContent().trim());
        row.setLikeCount(0);
        row.setReplyCount(0);
        row.setReportCount(0);
        row.setStatus(STATUS_NORMAL);
        row.setReplyToUserId(dto.getReplyToUserId());
        row.setCreatedAt(LocalDateTime.now());
        row.setUserId(userId);
        productCommentMapper.insert(row);

        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, rootId)
                .setSql("reply_count = reply_count + 1"));

        syncCommentCount(row.getProductId(), 1);
        log.info("回复评论: replyId={}, rootId={}, userId={}", row.getId(), rootId, userId);
        return toView(row);
    }

    @Override
    public Integer like(Long userId, Long commentId) {
        requireLogin(userId);
        ProductCommentEntity doc = requireVisible(commentId);
        String guardKey = LIKE_GUARD_PREFIX + commentId + ":" + userId;
        if (guardHit(guardKey)) {
            return likeCountOf(doc);
        }
        CommentLikeEntity like = new CommentLikeEntity();
        like.setId(IdWorker.getId());
        like.setCommentId(commentId);
        like.setUserId(userId);
        like.setCreatedAt(LocalDateTime.now());
        if (!insertOnceLike(like)) {
            rememberGuard(guardKey);
            return likeCountOf(doc);
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, commentId)
                .setSql("like_count = like_count + 1"));
        rememberGuard(guardKey);
        return likeCountOf(findById(commentId));
    }

    @Override
    public Integer unlike(Long userId, Long commentId) {
        requireLogin(userId);
        ProductCommentEntity doc = requireVisible(commentId);
        String guardKey = LIKE_GUARD_PREFIX + commentId + ":" + userId;
        int removed = commentLikeMapper.delete(new LambdaQueryWrapper<CommentLikeEntity>()
                .eq(CommentLikeEntity::getCommentId, commentId)
                .eq(CommentLikeEntity::getUserId, userId));
        clearGuard(guardKey);
        if (removed == 0) {
            return likeCountOf(doc);
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, commentId)
                .gt(ProductCommentEntity::getLikeCount, 0)
                .setSql("like_count = like_count - 1"));
        return likeCountOf(findById(commentId));
    }

    @Override
    public void report(Long userId, Long commentId) {
        requireLogin(userId);
        ProductCommentEntity doc = requireVisible(commentId);
        String guardKey = REPORT_GUARD_PREFIX + commentId + ":" + userId;
        if (guardHit(guardKey)) {
            hideIfThreshold(doc);
            return;
        }
        CommentReportEntity report = new CommentReportEntity();
        report.setId(IdWorker.getId());
        report.setCommentId(commentId);
        report.setUserId(userId);
        report.setCreatedAt(LocalDateTime.now());
        if (!insertOnceReport(report)) {
            rememberGuard(guardKey);
            hideIfThreshold(doc);
            return;
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, commentId)
                .setSql("report_count = report_count + 1"));
        rememberGuard(guardKey);
        ProductCommentEntity current = findById(commentId);
        hideIfThreshold(current != null ? current : doc);
    }

    @Override
    public void delete(Long userId, Long commentId) {
        requireLogin(userId);
        ProductCommentEntity doc = findById(commentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!userId.equals(doc.getUserId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "只能删除自己的评论");
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, commentId)
                .set(ProductCommentEntity::getStatus, STATUS_DELETED));
        syncCommentCount(doc.getProductId(), -1);
        log.info("删除评论: commentId={}, userId={}", commentId, userId);
    }

    @Override
    public PageResult<CommentVO> adminPage(Long productId, Integer status, int page, int size) {
        LambdaQueryWrapper<ProductCommentEntity> w = new LambdaQueryWrapper<>();
        if (productId != null) {
            w.eq(ProductCommentEntity::getProductId, productId);
        }
        if (status != null) {
            w.eq(ProductCommentEntity::getStatus, status);
        }
        w.orderByDesc(ProductCommentEntity::getCreatedAt);
        return doPage(w, page, size);
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

    private PageResult<CommentVO> doPage(LambdaQueryWrapper<ProductCommentEntity> wrapper, int page, int size) {
        Page<ProductCommentEntity> mpPage = productCommentMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(page, size, mpPage.getTotal(), toViews(mpPage.getRecords()));
    }

    private ProductCommentEntity findById(Long id) {
        return productCommentMapper.selectById(id);
    }

    private ProductCommentEntity requireVisible(Long commentId) {
        ProductCommentEntity doc = findById(commentId);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        return doc;
    }

    private int likeCountOf(ProductCommentEntity doc) {
        return doc.getLikeCount() == null ? 0 : doc.getLikeCount();
    }

    private boolean insertOnceLike(CommentLikeEntity row) {
        try {
            commentLikeMapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private boolean insertOnceReport(CommentReportEntity row) {
        try {
            commentReportMapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

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

    private void hideIfThreshold(ProductCommentEntity doc) {
        int reportCount = doc.getReportCount() == null ? 0 : doc.getReportCount();
        if (reportCount < REPORT_HIDE_THRESHOLD || doc.getStatus() == null || doc.getStatus() != STATUS_NORMAL) {
            return;
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, doc.getId())
                .eq(ProductCommentEntity::getStatus, STATUS_NORMAL)
                .set(ProductCommentEntity::getStatus, STATUS_HIDDEN));
        log.warn("评论举报达阈值自动隐藏: commentId={}, reportCount={}", doc.getId(), reportCount);
    }

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
            List<CommentLikeEntity> likes = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLikeEntity>()
                            .in(CommentLikeEntity::getCommentId, ids)
                            .eq(CommentLikeEntity::getUserId, userId));
            Set<Long> likedIds = likes.stream().map(CommentLikeEntity::getCommentId).collect(Collectors.toSet());
            for (CommentVO view : views) {
                view.setLiked(likedIds.contains(view.getId()));
            }
        } catch (Exception e) {
            log.warn("查询当前用户点赞关系失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private void updateStatus(Long commentId, int status) {
        ProductCommentEntity doc = findById(commentId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        productCommentMapper.update(null, new LambdaUpdateWrapper<ProductCommentEntity>()
                .eq(ProductCommentEntity::getId, commentId)
                .set(ProductCommentEntity::getStatus, status));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void syncCommentCount(Long productId, int delta) {
        try {
            productCommentCounter.updateCommentCount(productId, delta);
        } catch (Exception ex) {
            log.warn("回写商品评论数失败，已跳过: productId={}, delta={}", productId, delta, ex);
        }
    }

    private CommentVO toView(ProductCommentEntity row) {
        return toViews(List.of(row)).get(0);
    }

    private List<CommentVO> toViews(List<ProductCommentEntity> rows) {
        List<CommentVO> views = rows.stream().map(this::toVO).collect(Collectors.toList());
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

    private CommentVO toVO(ProductCommentEntity row) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(row, vo);
        return vo;
    }
}
