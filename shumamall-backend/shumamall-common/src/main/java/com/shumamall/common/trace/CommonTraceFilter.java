package com.shumamall.common.trace;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 透传过滤器。
 * <p>
 * 在请求入口生成或透传 TraceId，写入 MDC，确保全链路日志可追踪。
 * 上游（Gateway）已携带 X-Trace-Id 则复用，否则生成新 UUID。
 * <p>
 * 日志配置中使用 {@code [%X{traceId}]} 输出 TraceId。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CommonTraceFilter implements Filter {

    /** TraceId 请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC key */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 从请求头获取 TraceId，没有则生成
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            // 写入 MDC
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // 响应头回写 TraceId，方便调试
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);

            chain.doFilter(request, response);
        } finally {
            // 请求结束清除 MDC，避免内存泄漏（线程池复用线程）
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
