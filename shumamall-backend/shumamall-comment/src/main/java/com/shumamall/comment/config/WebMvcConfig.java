package com.shumamall.comment.config;

import com.shumamall.comment.auth.OptionalAuthFilter;
import com.shumamall.common.auth.JwtUtils;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 注册 {@link OptionalAuthFilter}，拦截用户端评论接口：
 * 带 token 则解析用户身份写入 SecurityContext，未带则放行（匿名可查询）。
 * 管理端接口 /api/v1/admin/comment/** 不拦截（与其他管理端接口一致）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> optionalAuthFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OptionalAuthFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/comment/*");
        registration.setOrder(1);
        return registration;
    }
}
