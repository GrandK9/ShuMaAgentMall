package com.shumamall.user.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户模块 Web MVC：C 端用户接口 JWT 校验。
 */
@Configuration
public class UserWebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> userTokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/user/*");
        registration.setOrder(1);
        return registration;
    }
}
