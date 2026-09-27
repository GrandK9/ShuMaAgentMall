package com.shumamall.video.config;

import com.shumamall.common.auth.JwtUtils;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 注册 {@link OptionalTokenFilter}：拦截视频相关请求，带 token 则解析身份，
 * 未携带放行（读接口匿名可访问，写接口由 Service 层校验登录态）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册可选认证过滤器。
     *
     * @param jwtUtils JWT 工具类
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<Filter> optionalTokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OptionalTokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/video/*", "/api/v1/admin/video/*");
        registration.setOrder(1);
        return registration;
    }
}
