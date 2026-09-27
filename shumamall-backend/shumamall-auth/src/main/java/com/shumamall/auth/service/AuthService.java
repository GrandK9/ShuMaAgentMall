package com.shumamall.auth.service;

import com.shumamall.auth.dto.LoginReqDTO;
import com.shumamall.auth.dto.LoginRespDTO;
import com.shumamall.auth.dto.RegisterReqDTO;

/**
 * 认证服务接口。
 */
public interface AuthService {

    /**
     * 用户注册。
     *
     * @param req 注册请求
     * @return 登录响应（含 token）
     */
    LoginRespDTO register(RegisterReqDTO req);

    /**
     * 用户登录。
     *
     * @param req 登录请求
     * @return 登录响应（含 token）
     */
    LoginRespDTO login(LoginReqDTO req);
}
