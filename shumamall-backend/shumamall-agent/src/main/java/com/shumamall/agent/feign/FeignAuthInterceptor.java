package com.shumamall.agent.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器：透传上游 {@code Authorization} 请求头。
 * <p>
 * Agent 编排用户业务（购物车 / 订单 / 个人信息）时，下游 order / user 服务依赖
 * TokenFilter 从 JWT 解析 userId，因此必须把浏览器透传给 agent 的 token 继续透传下去，
 * 否则下游服务会返回 401。
 */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        String authHeader = attributes.getRequest().getHeader(AUTHORIZATION);
        if (authHeader != null && !authHeader.isBlank()) {
            template.header(AUTHORIZATION, authHeader);
        }
    }
}
