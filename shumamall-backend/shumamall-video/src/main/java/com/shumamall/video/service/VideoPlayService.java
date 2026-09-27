package com.shumamall.video.service;

import com.shumamall.video.dto.VideoPlayRespDTO;
import com.shumamall.video.dto.VideoProgressReqDTO;
import com.shumamall.video.dto.VideoProgressRespDTO;
import com.shumamall.video.dto.VideoMetaVO;

/**
 * 视频播放与断点续播服务接口。
 */
public interface VideoPlayService {

    /**
     * 生成播放地址（MinIO 签名 URL，1 小时有效）。
     *
     * @param videoId 视频 ID
     * @return 播放信息
     */
    VideoPlayRespDTO getPlayUrl(Long videoId);

    /**
     * 查询视频元数据（上传后轮询处理状态用）。
     *
     * @param videoId 视频 ID
     * @return 元数据视图
     */
    VideoMetaVO getMeta(Long videoId);

    /**
     * 上报播放进度（高频写 Redis，定时落库 MongoDB）。
     *
     * @param userId 用户 ID
     * @param dto    进度参数
     */
    void reportProgress(Long userId, VideoProgressReqDTO dto);

    /**
     * 查询播放进度（Redis 优先，MongoDB 兜底）。
     *
     * @param userId  用户 ID
     * @param videoId 视频 ID
     * @return 进度信息
     */
    VideoProgressRespDTO getProgress(Long userId, Long videoId);
}
