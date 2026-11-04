package com.shumamall.comment.config;

import com.shumamall.comment.auth.OptionalAuthFilter;
import com.shumamall.common.auth.JwtUtils;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 评论模块 Web MVC：用户端可选登录过滤器。
 */
@Configuration
public class CommentWebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> commentOptionalAuthFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OptionalAuthFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/comment/*");
        registration.setOrder(1);
        return registration;
    }
}
