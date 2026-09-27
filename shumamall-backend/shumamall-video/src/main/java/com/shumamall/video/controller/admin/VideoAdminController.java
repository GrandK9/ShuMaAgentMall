package com.shumamall.video.controller.admin;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.video.dto.VideoMetaVO;
import com.shumamall.video.dto.VideoPageQuery;
import com.shumamall.video.service.VideoAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频管理控制器（管理端）。
 * <p>
 * 视频列表 / 删除（清理 MinIO + MongoDB）/ 重新转码兜底。
 * 上传接口复用 {@link com.shumamall.video.controller.VideoUploadController}（init/chunk/complete）。
 * <p>
 * 三个接口均挂 {@link RequirePermission}：本服务的认证过滤器是
 * {@link com.shumamall.video.config.OptionalTokenFilter}（无 token 放行），
 * 若不在此处校验，删除接口会允许匿名调用并真实清理 MinIO 三桶与元数据。
 */
@Slf4j
@Tag(name = "视频-视频管理(后台)", description = "管理端视频列表查询、删除（清理 MinIO 与元数据）及重新转码")
@RestController
@RequestMapping("/api/v1/admin/video")
@RequiredArgsConstructor
public class VideoAdminController {

    private final VideoAdminService videoAdminService;

    /**
     * 分页查询视频列表。
     *
     * @param query 查询参数（status / uploaderType 筛选）
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission("video:view")
    public R<PageResult<VideoMetaVO>> page(@Valid VideoPageQuery query) {
        return R.ok(videoAdminService.page(query));
    }

    /**
     * 删除视频（清理 MinIO 原始文件 + HLS 分片 + MongoDB 元数据）。
     *
     * @param videoId 视频 ID
     * @return 操作结果
     */
    @DeleteMapping("/{videoId}")
    @RequirePermission("video:delete")
    public R<Void> delete(@PathVariable Long videoId) {
        videoAdminService.delete(videoId);
        log.info("管理端删除视频: videoId={}", videoId);
        return R.ok();
    }

    /**
     * 重新转码（管理端兜底：重新校验 + HLS 切片）。
     *
     * @param videoId 视频 ID
     * @return 操作结果
     */
    @PostMapping("/{videoId}/retranscode")
    @RequirePermission("video:retranscode")
    public R<Void> reTranscode(@PathVariable Long videoId) {
        videoAdminService.reTranscode(videoId);
        log.info("管理端触发重新转码: videoId={}", videoId);
        return R.ok();
    }
}
