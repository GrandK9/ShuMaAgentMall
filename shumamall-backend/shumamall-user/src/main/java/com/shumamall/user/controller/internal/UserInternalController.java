package com.shumamall.user.controller.internal;

import com.shumamall.common.result.R;
import com.shumamall.user.dto.UserBriefVO;
import com.shumamall.user.dto.UserVO;
import com.shumamall.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * 用户信息（供评论列表按用户 ID 取当前昵称和头像）。
     *
     * @param userId 用户 ID
     * @return 用户信息（脱敏）
     */
    @GetMapping("/info")
    public R<UserVO> getUserInfo(@RequestParam("userId") Long userId) {
        return R.ok(userService.getUserInfo(userId));
    }

    /**
     * 批量查询展示资料。评论列表按本页出现的用户 ID 一次取回。
     *
     * @param userIds 用户 ID
     * @return 展示资料
     */
    @PostMapping("/brief")
    public R<List<UserBriefVO>> listBrief(@RequestBody List<Long> userIds) {
        return R.ok(userService.listBrief(userIds));
    }
}
