package com.shumamall.comment.controller;

import com.shumamall.comment.dto.CommentVO;
import com.shumamall.comment.service.CommentService;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论区管理端控制器。
 * <p>
 * 管理端接口路径 /api/v1/admin/comment/**，与其他管理端接口一致不拦截 TokenFilter；
 * 权限控制（@RequirePermission）待管理端鉴权链路完善后接入。
 */
@Slf4j
@Validated
@Tag(name = "评论-评论管理(后台)", description = "管理端评论分页查询、违规隐藏与恢复")
@RestController
@RequestMapping("/api/v1/admin/comment")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentService commentService;

    /**
     * 分页查询评论（可按商品/状态过滤）。
     *
     * @param productId 商品 ID（可选）
     * @param status    状态 0-正常 1-作者删除 2-管理员隐藏（可选）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @return 评论分页
     */
    @GetMapping
    public R<PageResult<CommentVO>> page(@RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") @Min(1) int page,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return R.ok(commentService.adminPage(productId, status, page, size));
    }

    /**
     * 隐藏评论（内容违规）。
     *
     * @param commentId 评论 ID
     */
    @PutMapping("/{commentId}/hide")
    public R<Void> hide(@PathVariable Long commentId) {
        commentService.hide(commentId);
        return R.ok();
    }

    /**
     * 恢复评论。
     *
     * @param commentId 评论 ID
     */
    @PutMapping("/{commentId}/restore")
    public R<Void> restore(@PathVariable Long commentId) {
        commentService.restore(commentId);
        return R.ok();
    }
}
