package com.shumamall.common.auth;

import com.shumamall.common.result.R;
import com.shumamall.common.result.ResponseWriter;
import com.shumamall.common.result.ResultCode;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * JWT Token 校验过滤器。
 * <p>
 * 从请求头 {@code Authorization: Bearer <token>} 中提取 JWT，
 * 解析出 userId 并写入 {@link SecurityContext}。
 * <p>
 * 两种模式：
 * <ul>
 *   <li>{@code required = true}（默认）：缺失/无效 token 直接返回 401，用于登录后才可访问的接口；</li>
 *   <li>{@code required = false}：缺失 token 时匿名放行，仅在有 token 时解析并写入上下文。
 *       用于同一路径前缀下既有需登录接口（由 {@code @RequirePermission} 切面判定并拒绝）
 *       又有服务间 Feign 读取（不带 token）的场景。</li>
 * </ul>
 * 使用方式：在各微服务的 WebMvcConfig 中注册此 Filter，并配置需要拦截的 URL 路径。
 */
@Slf4j
public class TokenFilter implements Filter {

    private final JwtUtils jwtUtils;

    /** true=必须携带有效 token；false=允许匿名，仅在有 token 时解析并写入上下文 */
    private final boolean required;

    public TokenFilter(JwtUtils jwtUtils) {
        this(jwtUtils, true);
    }

    public TokenFilter(JwtUtils jwtUtils, boolean required) {
        this.jwtUtils = jwtUtils;
        this.required = required;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (required) {
                writeUnauthorized(response, ResultCode.UNAUTHORIZED, "未提供认证令牌");
                return;
            }
            // 匿名放行：受保护接口由 @RequirePermission 切面在缺少 userId 时拒绝
            chain.doFilter(request, response);
            return;
        }

        try {
            Long userId = jwtUtils.getUserId(authHeader.substring(7));
            if (userId == null) {
                writeUnauthorized(response, ResultCode.TOKEN_INVALID, "无效的认证令牌");
                return;
            }

            SecurityContext.setUserId(userId);
            chain.doFilter(request, response);

        } finally {
            SecurityContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        ResponseWriter.writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, R.failed(resultCode, message));
    }
}
