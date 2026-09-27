package com.shumamall.user.config;

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
 * 注册 {@link TokenFilter}，拦截所有 {@code /api/v1/user/**} 请求，
 * 校验 JWT token 并将 userId 写入 {@link com.shumamall.common.auth.SecurityContext}。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> tokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/user/*");
        registration.setOrder(1);
        return registration;
    }
}
