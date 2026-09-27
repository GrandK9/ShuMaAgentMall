package com.shumamall.video.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.video.dto.VideoMetaVO;
import com.shumamall.video.dto.VideoPageQuery;

/**
 * 视频管理端服务接口（列表 / 删除 / 重新转码）。
 */
public interface VideoAdminService {

    /**
     * 分页查询视频列表。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<VideoMetaVO> page(VideoPageQuery query);

    /**
     * 删除视频（清理 MinIO 原始文件 + HLS 分片 + MongoDB 元数据）。
     *
     * @param videoId 视频 ID
     */
    void delete(Long videoId);

    /**
     * 重新转码（管理端兜底）。
     *
     * @param videoId 视频 ID
     */
    void reTranscode(Long videoId);
}
