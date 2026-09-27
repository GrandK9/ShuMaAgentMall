package com.shumamall.payment.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.TokenFilter;
import com.shumamall.common.sign.RequestSignFilter;
import com.shumamall.common.sign.SignSecretResolver;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Web 配置。
 * <p>
 * 注册 JWT Token 过滤器与请求签名过滤器，拦截需要认证的请求路径。
 */
@Configuration
public class WebConfig {

    /**
     * 注册 TokenFilter，拦截支付相关接口。
     *
     * @param jwtUtils JWT 工具类
     * @return Filter 注册 Bean
     */
    @Bean
    public FilterRegistrationBean<Filter> tokenFilter(JwtUtils jwtUtils) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenFilter(jwtUtils));
        registration.addUrlPatterns("/api/v1/payment/*", "/api/v1/admin/payment/*");
        registration.setName("tokenFilter");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册请求签名过滤器（HMAC-SHA256 + 时间窗 + nonce 防重放）。
     * <p>
     * 挂在 payment 的「POST /api/v1/payment/pay」（支付）与
     * 「POST /api/v1/admin/payment/{id}/refund」（退款）上——两条链路都会真实动钱，
     * 光有 token 只能证明"是谁"，签名才能同时证明"这次请求的内容没被改过、也没被重放"。
     *
     * @param jwtUtils           JWT 工具类
     * @param signSecretResolver 用户签名密钥解析器
     * @param redisTemplate      防重放 nonce 存储
     * @param windowSeconds      允许的时间偏差（秒），可用 shumamall.sign.window-seconds 覆盖
     * @return Filter 注册 Bean
     */
    @Bean
    public FilterRegistrationBean<RequestSignFilter> requestSignFilterRegistration(
            JwtUtils jwtUtils,
            SignSecretResolver signSecretResolver,
            StringRedisTemplate redisTemplate,
            @Value("${shumamall.sign.window-seconds:300}") long windowSeconds) {
        FilterRegistrationBean<RequestSignFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestSignFilter(jwtUtils, signSecretResolver, redisTemplate, windowSeconds));
        registration.addUrlPatterns("/api/v1/payment/pay", "/api/v1/admin/payment/*");
        registration.setName("requestSignFilter");
        // 排在 tokenFilter（order=1）之后：签名校验依赖 token 解析出的 userId
        registration.setOrder(2);
        return registration;
    }
}
