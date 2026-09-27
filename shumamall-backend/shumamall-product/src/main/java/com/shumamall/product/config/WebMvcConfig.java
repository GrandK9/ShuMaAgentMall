package com.shumamall.product.config;

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
 * 为管理端接口注册 JWT 解析过滤器，把 userId 写入
 * {@link com.shumamall.common.auth.SecurityContext}，供
 * {@code @RequirePermission} 切面做权限校验。
 * <p>
 * 这里使用非强制模式（{@code required = false}）：{@code /api/v1/admin/**} 下
 * 既有需要登录的管理端写接口（切面会拒绝无 userId 的请求），
 * 也有服务间 Feign 读取（如 search 全量灌库拉商品、admin 端仪表盘统计），
 * 后者不携带 token，强制模式会把它们一并拦掉。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> adminTokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils, false));
        registration.addUrlPatterns("/api/v1/admin/*");
        registration.setOrder(1);
        return registration;
    }
}
