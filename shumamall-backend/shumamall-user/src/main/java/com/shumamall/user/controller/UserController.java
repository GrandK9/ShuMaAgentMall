package com.shumamall.user.controller;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.user.dto.UserVO;
import com.shumamall.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息控制器。
 */
@Tag(name = "用户-个人信息", description = "当前登录用户的资料查询与修改接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息。
     */
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public R<UserVO> getUserInfo() {
        Long userId = SecurityContext.getUserId();
        log.debug("查询用户信息: userId={}", userId);
        UserVO userVO = userService.getUserInfo(userId);
        return R.ok(userVO);
    }

    /**
     * 更新当前登录用户信息。
     */
    @Operation(summary = "更新当前登录用户信息")
    @PutMapping("/info")
    public R<Void> updateUserInfo(@RequestParam(required = false) String phone,
                                   @RequestParam(required = false) String email,
                                   @RequestParam(required = false) String nickname,
                                   @RequestParam(required = false) Integer gender) {
        Long userId = SecurityContext.getUserId();
        log.debug("更新用户信息: userId={}", userId);
        userService.updateUserInfo(userId, phone, email, nickname, gender);
        return R.ok();
    }
}
