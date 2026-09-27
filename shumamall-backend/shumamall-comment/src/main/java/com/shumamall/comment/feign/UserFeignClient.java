package com.shumamall.comment.feign;

import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 客户端。
 * <p>
 * 评论服务在发表评论/回复时获取用户名与头像快照，避免展示时频繁服务间调用。
 * 内部接口路径 {@code /api/v1/internal/user} 不经过 user 服务 TokenFilter。
 * <p>
 * 用户服务不可用（超时/报错/熔断打开）时由 {@link UserFeignFallbackFactory} 返回降级结果，
 * 保证发表评论主流程不被阻断。
 */
@FeignClient(name = "shumamall-user", path = "/api/v1/internal/user",
        fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 查询用户信息。
     *
     * @param userId 用户 ID
     * @return 用户信息（脱敏）
     */
    @GetMapping("/info")
    R<UserInfoDTO> getUserInfo(@RequestParam("userId") Long userId);
}
