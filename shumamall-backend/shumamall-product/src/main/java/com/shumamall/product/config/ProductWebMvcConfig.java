package com.shumamall.product.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 商品模块 Web MVC：管理端 JWT 非强制解析。
 */
@Configuration
public class ProductWebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> productAdminTokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils, false));
        registration.addUrlPatterns("/api/v1/admin/*");
        registration.setOrder(1);
        return registration;
    }
}
