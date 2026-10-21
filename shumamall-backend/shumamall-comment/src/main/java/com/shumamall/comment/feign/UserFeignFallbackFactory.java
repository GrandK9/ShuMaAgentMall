package com.shumamall.comment.feign;

import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户服务不可用时，评论列表展示名降级为「用户{id}」，发表评论本身不依赖这次查询。
 */
@Slf4j
@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        return new UserFeignClient() {
            @Override
            public R<UserInfoDTO> getUserInfo(Long userId) {
                log.warn("用户服务调用失败: userId={}, cause={}", userId, cause.toString());
                return R.failed(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂不可用");
            }

            @Override
            public R<List<UserInfoDTO>> listBrief(List<Long> userIds) {
                log.warn("用户服务批量查询失败，评论展示名降级: size={}, cause={}",
                        userIds == null ? 0 : userIds.size(), cause.toString());
                return R.failed(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂不可用");
            }
        };
    }
}
