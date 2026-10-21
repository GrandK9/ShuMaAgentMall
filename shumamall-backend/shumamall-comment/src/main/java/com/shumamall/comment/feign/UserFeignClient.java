package com.shumamall.comment.feign;

import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 客户端。
 * <p>
 * 评论列表只存用户 ID，返回前按本页出现的 ID 批量取当前昵称和头像。
 * 内部接口路径 {@code /api/v1/internal/user} 不经过 user 服务 TokenFilter。
 */
@FeignClient(name = "shumamall-user", path = "/api/v1/internal/user",
        fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 查询单个用户信息。
     *
     * @param userId 用户 ID
     * @return 用户信息（脱敏）
     */
    @GetMapping("/info")
    R<UserInfoDTO> getUserInfo(@RequestParam("userId") Long userId);

    /**
     * 批量查询展示资料。
     *
     * @param userIds 用户 ID
     * @return 当前昵称、登录名和头像
     */
    @PostMapping("/brief")
    R<List<UserInfoDTO>> listBrief(@RequestBody List<Long> userIds);
}
