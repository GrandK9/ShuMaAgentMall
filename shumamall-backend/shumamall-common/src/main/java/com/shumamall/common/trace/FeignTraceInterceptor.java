package com.shumamall.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器 — 自动透传 TraceId。
 * <p>
 * 将当前线程 MDC 中的 traceId 写入 Feign 请求头 X-Trace-Id，
 * 下游微服务拾取后继续透传，实现跨服务日志串联。
 */
@Slf4j
@Component
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(CommonTraceFilter.TRACE_ID_MDC_KEY);
        if (traceId != null) {
            template.header(CommonTraceFilter.TRACE_ID_HEADER, traceId);
        }
    }
}
