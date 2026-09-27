package com.shumamall.permission.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 注册 {@link TokenFilter}，拦截权限管理相关请求，
 * 校验 JWT token 并将 userId 写入 {@link com.shumamall.common.auth.SecurityContext}。
 * <p>
 * 注意：内部接口 {@code /api/permission/internal/**} 不拦截，
 * 供其他服务 {@link com.shumamall.common.perm.aspect.PermissionAspect} 无 token 直连校验。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> tokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        // /permission/menus 不在 /api/v1 前缀下（网关与前端代理均按此路径路由，不可改动），
        // 若只拦 /api/v1/admin/* 则该接口拿不到 userId，菜单树恒为空。
        registration.addUrlPatterns("/api/v1/admin/*", "/permission/*");
        registration.setOrder(1);
        return registration;
    }
}
