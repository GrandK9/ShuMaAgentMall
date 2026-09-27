package com.shumamall.auth.service.impl;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.auth.dao.UserMapper;
import com.shumamall.auth.dto.LoginReqDTO;
import com.shumamall.auth.dto.LoginRespDTO;
import com.shumamall.auth.dto.RegisterReqDTO;
import com.shumamall.auth.entity.UserEntity;
import com.shumamall.auth.service.AuthService;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.common.sign.SignSecretResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 认证服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final SignSecretResolver signSecretResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO register(RegisterReqDTO req) {
        // 检查用户名是否已存在
        UserEntity existing = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, req.getUsername()));
        if (existing != null) {
            throw new BusinessException(ResultCode.USER_EXISTS, "用户名已存在");
        }

        // 创建用户
        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        // BCrypt 加密
        user.setPassword(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(req.getPassword(),
                org.springframework.security.crypto.bcrypt.BCrypt.gensalt()));
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setNickname(req.getUsername());
        user.setStatus(1);

        user.setRole("user");

        userMapper.insert(user);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        // 签发 token
        List<String> roles = Collections.singletonList(user.getRole());
        String token = jwtUtils.generateToken(user.getId(), roles);
        return new LoginRespDTO(token, signSecretResolver.resolveFromToken(token), user.getId(),
                user.getUsername(), roles);
    }

    @Override
    public LoginRespDTO login(LoginReqDTO req) {
        // 查询用户
        UserEntity user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, req.getUsername()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户名或密码错误");
        }

        // 校验密码
        if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户名或密码错误");
        }

        // 检查状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 签发 token，从数据库中读取角色
        List<String> roles = user.getRole() != null
                ? Collections.singletonList(user.getRole())
                : Collections.singletonList("user");
        String token = jwtUtils.generateToken(user.getId(), roles);
        return new LoginRespDTO(token, signSecretResolver.resolveFromToken(token), user.getId(),
                user.getUsername(), roles);
    }
}
