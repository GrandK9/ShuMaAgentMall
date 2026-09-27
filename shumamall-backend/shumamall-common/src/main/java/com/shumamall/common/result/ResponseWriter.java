package com.shumamall.common.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 直接向 HTTP 响应写出 {@link R} 结构的工具。
 * <p>
 * 用于 Filter 等**早于 Spring MVC 异常处理器**执行的组件：这些组件抛出的异常不会被
 * {@code @RestControllerAdvice} 捕获，必须自行写回响应体。若各处手工拼 JSON，
 * 极易与 {@code R} 的字段名（{@code code}/{@code msg}/{@code data}）和业务码约定脱节，
 * 前端也就读不到真实的失败原因。
 * <p>
 * 统一由此写出，保证「Filter 拒绝」与「Controller 业务失败」返回同一套契约。
 */
@Slf4j
public final class ResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ResponseWriter() {
    }

    /**
     * 将 {@link R} 序列化后写入响应，并设置 HTTP 状态码。
     *
     * @param response   HTTP 响应
     * @param httpStatus HTTP 状态码，如 401 / 403
     * @param body       统一返回体，通常为 {@code R.failed(...)}
     */
    public static void writeJson(HttpServletResponse response, int httpStatus, R<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(OBJECT_MAPPER.writeValueAsBytes(body));
    }
}
