package com.shumamall.video.config;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResponseWriter;
import com.shumamall.common.result.ResultCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 可选认证过滤器（视频服务专用）。
 * <p>
 * 播放/进度查询等读接口允许匿名访问（商品详情页未登录也能看视频），
 * 上传/上报等写接口由 Service 层校验登录态。
 * 请求头带 token 则解析身份写入 {@link SecurityContext}；未携带则放行；
 * token 存在但无效时返回 401，防止伪造身份。
 */
@Slf4j
public class OptionalTokenFilter implements Filter {

    private final JwtUtils jwtUtils;

    public OptionalTokenFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                // 匿名请求直接放行，由 Service 层决定是否需要登录
                chain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtils.getUserId(token);
            if (userId == null) {
                ResponseWriter.writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        R.failed(ResultCode.TOKEN_INVALID, "无效的认证令牌"));
                return;
            }

            SecurityContext.setUserId(userId);
            chain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }
}
