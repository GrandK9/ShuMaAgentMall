package com.shumamall.permission.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 权限模块 Web MVC：管理端与 /permission 菜单 JWT 校验。
 */
@Configuration
public class PermissionWebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> permissionTokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/admin/*", "/permission/*");
        registration.setOrder(1);
        return registration;
    }
}
