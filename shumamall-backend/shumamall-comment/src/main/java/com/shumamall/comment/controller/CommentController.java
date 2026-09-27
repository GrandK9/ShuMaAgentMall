package com.shumamall.comment.controller;

import com.shumamall.comment.dto.CommentCreateDTO;
import com.shumamall.comment.dto.CommentVO;
import com.shumamall.comment.dto.ReplyCreateDTO;
import com.shumamall.comment.service.CommentService;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论区用户端控制器。
 * <p>
 * 查询接口允许匿名访问（商品详情页未登录也能看评论），
 * 写接口由 Service 层校验登录态（SecurityContext 中的 userId）。
 */
@Tag(name = "评论-用户端", description = "商品评论的查询、发表、回复、点赞、举报与删除接口")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 全站热门评论榜（跨商品，按点赞数倒序）。
     *
     * @param limit 返回条数（默认 10，最大 50）
     * @return 热门根评论列表
     */
    @Operation(summary = "全站热门评论榜（跨商品，按点赞数倒序）")
    @GetMapping("/hot")
    public R<List<CommentVO>> hotComments(
            @Parameter(description = "返回条数，默认 10，最大 50")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return R.ok(commentService.hotComments(limit));
    }

    /**
     * 分页查询商品根评论。
     *
     * @param productId 商品 ID
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @param sortBy    排序：latest-最新（默认）hot-最热
     * @return 根评论分页
     */
    @Operation(summary = "分页查询商品根评论")
    @GetMapping("/product/{productId}")
    public R<PageResult<CommentVO>> pageRootComments(
            @Parameter(description = "商品 ID") @PathVariable Long productId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1-50") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @Parameter(description = "排序：latest-最新（默认）hot-最热") @RequestParam(defaultValue = "latest") String sortBy) {
        return R.ok(commentService.pageRootComments(productId, page, size, sortBy));
    }

    /**
     * 分页查询根评论下的回复。
     *
     * @param rootId 根评论 ID
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 回复分页
     */
    @Operation(summary = "分页查询根评论下的回复")
    @GetMapping("/root/{rootId}")
    public R<PageResult<CommentVO>> pageReplies(
            @Parameter(description = "根评论 ID") @PathVariable Long rootId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1-50") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return R.ok(commentService.pageReplies(rootId, page, size));
    }

    /**
     * 发表评论。
     *
     * @param dto 评论请求
     * @return 评论视图
     */
    @Operation(summary = "发表评论")
    @PostMapping
    public R<CommentVO> createComment(@Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(commentService.createComment(SecurityContext.getUserId(), dto));
    }

    /**
     * 回复评论。
     *
     * @param commentId 根评论 ID
     * @param dto       回复请求
     * @return 回复视图
     */
    @Operation(summary = "回复评论")
    @PostMapping("/{commentId}/reply")
    public R<CommentVO> reply(@Parameter(description = "根评论 ID") @PathVariable Long commentId,
                              @Valid @RequestBody ReplyCreateDTO dto) {
        return R.ok(commentService.reply(SecurityContext.getUserId(), commentId, dto));
    }

    /**
     * 点赞评论（幂等）。
     *
     * @param commentId 评论 ID
     * @return 点赞后的点赞数
     */
    @Operation(summary = "点赞评论（幂等）")
    @PostMapping("/{commentId}/like")
    public R<Integer> like(@Parameter(description = "评论 ID") @PathVariable Long commentId) {
        return R.ok(commentService.like(SecurityContext.getUserId(), commentId));
    }

    /**
     * 举报评论。
     *
     * @param commentId 评论 ID
     */
    @Operation(summary = "举报评论")
    @PostMapping("/{commentId}/report")
    public R<Void> report(@Parameter(description = "评论 ID") @PathVariable Long commentId) {
        commentService.report(SecurityContext.getUserId(), commentId);
        return R.ok();
    }

    /**
     * 删除自己的评论。
     *
     * @param commentId 评论 ID
     */
    @Operation(summary = "删除自己的评论")
    @DeleteMapping("/{commentId}")
    public R<Void> delete(@Parameter(description = "评论 ID") @PathVariable Long commentId) {
        commentService.delete(SecurityContext.getUserId(), commentId);
        return R.ok();
    }
}
