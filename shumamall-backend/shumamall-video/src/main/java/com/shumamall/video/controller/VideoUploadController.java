package com.shumamall.video.controller;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.video.constant.VideoConstants;
import com.shumamall.video.dto.CompleteUploadReqDTO;
import com.shumamall.video.dto.CompleteUploadRespDTO;
import com.shumamall.video.dto.InitUploadReqDTO;
import com.shumamall.video.dto.InitUploadRespDTO;
import com.shumamall.video.dto.UploadRespDTO;
import com.shumamall.video.dto.VideoMetaVO;
import com.shumamall.video.dto.VideoPlayRespDTO;
import com.shumamall.video.dto.VideoProgressReqDTO;
import com.shumamall.video.dto.VideoProgressRespDTO;
import com.shumamall.video.entity.UploadSessionDoc;
import com.shumamall.video.service.VideoPlayService;
import com.shumamall.video.service.VideoUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频 API 控制器（用户端/小程序/第三方 API 统一入口）。
 * <p>
 * 分片上传（管理端大文件）/ 单次直传（评论区小文件）/
 * 播放签名 URL / 断点续播进度上报。
 */
@Slf4j
@Tag(name = "视频-文件上传", description = "视频分片/直传上传、播放地址生成、元数据查询与播放进度上报接口")
@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoUploadController {

    private final VideoUploadService videoUploadService;
    private final VideoPlayService videoPlayService;

    /**
     * 初始化分片上传会话。
     *
     * @param dto 初始化参数（uploaderType: admin/user，决定时长/大小上限）
     * @return 会话信息
     */
    @PostMapping("/init")
    public R<InitUploadRespDTO> initUpload(@Valid @RequestBody InitUploadReqDTO dto) {
        Long userId = requireLogin();
        return R.ok(videoUploadService.initUpload(userId, dto));
    }

    /**
     * 上传单个分片。
     *
     * @param sessionId  会话 ID
     * @param chunkIndex 分片索引
     * @param data       分片内容
     * @return 操作结果
     */
    @PostMapping("/chunk")
    public R<Void> uploadChunk(@RequestParam String sessionId,
                               @RequestParam Integer chunkIndex,
                               @RequestParam("data") MultipartFile data) {
        requireLogin();
        videoUploadService.uploadChunk(sessionId, chunkIndex, data);
        return R.ok();
    }

    /**
     * 合并分片并触发后台校验 + HLS 切片。
     *
     * @param dto 合并参数
     * @return 合并结果（videoId 可能处理中为空）
     */
    @PostMapping("/complete")
    public R<CompleteUploadRespDTO> completeUpload(@Valid @RequestBody CompleteUploadReqDTO dto) {
        requireLogin();
        return R.ok(videoUploadService.completeUpload(dto.getSessionId()));
    }

    /**
     * 查询上传会话（断点续传：返回已传分片索引列表）。
     *
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    @GetMapping("/session/{sessionId}")
    public R<UploadSessionDoc> getSession(@PathVariable String sessionId) {
        return R.ok(videoUploadService.getSession(sessionId));
    }

    /**
     * 单次直传（评论区小文件：≤100MB / 60s，服务端同步校验 + 切片）。
     *
     * @param file         视频文件
     * @param uploaderType 上传者类型 admin / user
     * @param sourceType   上传来源 browser / miniapp / api
     * @return 处理结果（含 videoId / 状态 / 失败原因）
     */
    @PostMapping("/upload")
    public R<UploadRespDTO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = VideoConstants.UPLOADER_USER) String uploaderType,
                                   @RequestParam(defaultValue = VideoConstants.SOURCE_MINIAPP) String sourceType) {
        Long userId = requireLogin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请选择视频文件");
        }
        return R.ok(videoUploadService.uploadDirect(userId, uploaderType, sourceType, file));
    }

    /**
     * 生成播放地址（MinIO 签名 URL，1 小时有效）。
     *
     * @param videoId 视频 ID
     * @return 播放信息
     */
    @GetMapping("/play/{videoId}")
    public R<VideoPlayRespDTO> play(@PathVariable Long videoId) {
        return R.ok(videoPlayService.getPlayUrl(videoId));
    }

    /**
     * 查询视频元数据（上传后轮询处理状态用）。
     *
     * @param videoId 视频 ID
     * @return 元数据视图
     */
    @GetMapping("/meta/{videoId}")
    public R<VideoMetaVO> meta(@PathVariable Long videoId) {
        return R.ok(videoPlayService.getMeta(videoId));
    }

    /**
     * 上报播放进度（前端每 10 秒调用一次）。
     *
     * @param dto 进度参数
     * @return 操作结果
     */
    @PostMapping("/progress")
    public R<Void> reportProgress(@Valid @RequestBody VideoProgressReqDTO dto) {
        Long userId = requireLogin();
        videoPlayService.reportProgress(userId, dto);
        return R.ok();
    }

    /**
     * 查询播放进度（断点续播）。
     *
     * @param videoId 视频 ID
     * @return 进度信息
     */
    @GetMapping("/progress/{videoId}")
    public R<VideoProgressRespDTO> getProgress(@PathVariable Long videoId) {
        Long userId = requireLogin();
        return R.ok(videoPlayService.getProgress(userId, videoId));
    }

    /**
     * 获取当前登录用户 ID，未登录抛 401。
     *
     * @return 用户 ID
     */
    private Long requireLogin() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
