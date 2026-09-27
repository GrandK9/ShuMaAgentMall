package com.shumamall.agent.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 配置：注册 JWT Token 过滤器。
 * <p>
 * 拦截 {@code /api/v1/agent/*}：所有 Agent 对话请求必须携带
 * {@code Authorization: Bearer <token>}，解析出的 userId 写入
 * {@link com.shumamall.common.auth.SecurityContext}，供编排器注入工具上下文。
 * 与 order / user 服务的拦截配置保持一致。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig {

    private final JwtUtils jwtUtils;

    /**
     * 注册 Token 过滤器，拦截需要登录的 Agent API 路径。
     */
    @Bean
    public FilterRegistrationBean<TokenFilter> tokenFilterRegistration() {
        FilterRegistrationBean<TokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/agent/*");
        registration.setOrder(1);
        return registration;
    }
}
