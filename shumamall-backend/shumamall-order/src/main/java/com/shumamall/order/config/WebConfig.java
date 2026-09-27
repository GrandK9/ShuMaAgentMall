package com.shumamall.order.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import com.shumamall.common.sign.RequestSignFilter;
import com.shumamall.common.sign.SignSecretResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Web 配置，注册 Token 鉴权过滤器与请求签名过滤器。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final JwtUtils jwtUtils;

    /**
     * 注册 Token 过滤器，拦截需要登录的 API 路径。
     */
    @Bean
    public FilterRegistrationBean<TokenFilter> tokenFilterRegistration() {
        FilterRegistrationBean<TokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/cart/*", "/api/v1/orders/*", "/api/v1/admin/orders/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册请求签名过滤器（HMAC-SHA256 + 时间窗 + nonce 防重放），拦截资金/状态相关的写接口。
     * <p>
     * 挂在 order 的「POST /api/v1/orders」（下单）与「PUT /api/v1/admin/orders/{id}/status」（改状态）上。
     * 这里用前缀匹配而不是逐个方法暴露，是为了让同前缀下以后新增的写接口自动受到保护；
     * 读接口不受影响——{@link RequestSignFilter} 内部只拦 POST/PUT/PATCH/DELETE。
     *
     * @param signSecretResolver 用户签名密钥解析器
     * @param redisTemplate      防重放 nonce 存储
     * @param windowSeconds      允许的时间偏差（秒），可用 shumamall.sign.window-seconds 覆盖
     * @return Filter 注册 Bean
     */
    @Bean
    public FilterRegistrationBean<RequestSignFilter> requestSignFilterRegistration(
            SignSecretResolver signSecretResolver,
            StringRedisTemplate redisTemplate,
            @Value("${shumamall.sign.window-seconds:300}") long windowSeconds) {
        FilterRegistrationBean<RequestSignFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestSignFilter(jwtUtils, signSecretResolver, redisTemplate, windowSeconds));
        registration.addUrlPatterns("/api/v1/orders", "/api/v1/admin/orders/*");
        registration.setName("requestSignFilter");
        // 排在 tokenFilter（order=1）之后：签名校验依赖 token 解析出的 userId，
        // 先由 TokenFilter 完成认证语义，再做「这次请求内容有没有被改过」的签名校验。
        registration.setOrder(2);
        return registration;
    }
}
