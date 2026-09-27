package com.shumamall.auth.controller;

import com.shumamall.auth.dto.LoginReqDTO;
import com.shumamall.auth.dto.LoginRespDTO;
import com.shumamall.auth.dto.RegisterReqDTO;
import com.shumamall.auth.service.AuthService;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器。
 * <p>
 * 提供注册和登录接口，经过网关路由到本服务。
 */
@Tag(name = "认证-登录注册", description = "提供用户注册与登录接口，成功后返回 token 与用户信息")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册。
     *
     * @param req 注册请求
     * @return token + 用户信息
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<LoginRespDTO> register(@Valid @RequestBody RegisterReqDTO req) {
        LoginRespDTO result = authService.register(req);
        return R.ok(result);
    }

    /**
     * 用户登录。
     *
     * @param req 登录请求
     * @return token + 用户信息
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO req) {
        LoginRespDTO result = authService.login(req);
        return R.ok(result);
    }
}
