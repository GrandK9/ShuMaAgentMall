package com.shumamall.user.controller.internal;

import com.shumamall.common.result.R;
import com.shumamall.user.dto.UserVO;
import com.shumamall.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户内部控制器（仅服务间 Feign 调用，不经过 TokenFilter 认证）。
 */
@Slf4j
@Tag(name = "内部接口-用户", description = "微服务间 Feign 调用的用户总数统计与用户信息查询接口")
@RestController
@RequestMapping("/api/v1/internal/user")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /**
     * 用户总数（供管理端仪表盘聚合调用）。
     *
     * @return 用户总数
     */
    @GetMapping("/count")
    public R<Long> countUsers() {
        return R.ok(userService.countUsers());
    }

    /**
     * 用户信息（供评论等服务保存用户名/头像快照）。
     *
     * @param userId 用户 ID
     * @return 用户信息（脱敏）
     */
    @GetMapping("/info")
    public R<UserVO> getUserInfo(@RequestParam("userId") Long userId) {
        return R.ok(userService.getUserInfo(userId));
    }
}
