package com.shumamall.comment.service;

import com.shumamall.comment.dto.CommentCreateDTO;
import com.shumamall.comment.dto.CommentVO;
import com.shumamall.comment.dto.ReplyCreateDTO;
import com.shumamall.common.result.PageResult;

import java.util.List;

/**
 * 评论服务接口。
 */
public interface CommentService {

    /**
     * 分页查询某商品的根评论（不含回复）。
     *
     * @param productId 商品 ID
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @param sortBy    排序：latest-最新（默认）hot-最热
     * @return 根评论分页
     */
    PageResult<CommentVO> pageRootComments(Long productId, int page, int size, String sortBy);

    /**
     * 全站热门评论榜（跨商品，按点赞数倒序）。
     *
     * @param limit 返回条数
     * @return 热门根评论列表
     */
    List<CommentVO> hotComments(int limit);

    /**
     * 分页查询某根评论下的回复（时间正序）。
     *
     * @param rootId 根评论 ID
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 回复分页
     */
    PageResult<CommentVO> pageReplies(Long rootId, int page, int size);

    /**
     * 发表评论。
     *
     * @param userId 当前登录用户 ID
     * @param dto    评论请求
     * @return 评论视图
     */
    CommentVO createComment(Long userId, CommentCreateDTO dto);

    /**
     * 回复评论。
     *
     * @param userId  当前登录用户 ID
     * @param rootId  根评论 ID
     * @param dto     回复请求
     * @return 回复视图
     */
    CommentVO reply(Long userId, Long rootId, ReplyCreateDTO dto);

    /**
     * 点赞评论（幂等：同一用户重复点赞不累加）。
     *
     * @param userId    当前登录用户 ID
     * @param commentId 评论 ID
     * @return 点赞后的点赞数
     */
    Integer like(Long userId, Long commentId);

    /**
     * 举报评论（举报数达到阈值自动隐藏）。
     *
     * @param userId    当前登录用户 ID
     * @param commentId 评论 ID
     */
    void report(Long userId, Long commentId);

    /**
     * 删除评论（软删除，仅作者本人）。
     *
     * @param userId    当前登录用户 ID
     * @param commentId 评论 ID
     */
    void delete(Long userId, Long commentId);

    /**
     * 管理端分页查询评论（可按商品/状态过滤）。
     *
     * @param productId 商品 ID（可选）
     * @param status    状态（可选）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @return 评论分页
     */
    PageResult<CommentVO> adminPage(Long productId, Integer status, int page, int size);

    /**
     * 管理端隐藏评论。
     *
     * @param commentId 评论 ID
     */
    void hide(Long commentId);

    /**
     * 管理端恢复评论。
     *
     * @param commentId 评论 ID
     */
    void restore(Long commentId);
}
