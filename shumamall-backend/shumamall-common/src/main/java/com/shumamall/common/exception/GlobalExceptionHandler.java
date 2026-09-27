package com.shumamall.common.exception;

import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 统一捕获并返回 {@link R} 格式的错误响应，避免堆栈信息泄露到前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常。
     * <p>
     * HTTP 状态码由业务码推导，认证/权限类错误落到 401/403，
     * 与 Filter 层直接写出的响应保持一致（见 {@code ResponseWriter}）：
     * 前端、网关、监控都能按 HTTP 语义识别"未登录"与"无权限"，
     * 而不是一律当成参数错误（400）。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(R.failed(e.getCode(), e.getMessage()));
    }

    /**
     * 业务码 → HTTP 状态码。
     */
    private HttpStatus toHttpStatus(int code) {
        if (code == ResultCode.UNAUTHORIZED.getCode()
                || code == ResultCode.TOKEN_INVALID.getCode()
                || code == ResultCode.TOKEN_EXPIRED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ResultCode.FORBIDDEN.getCode()
                || code == ResultCode.PERMISSION_DENIED.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ResultCode.NONCE_REPLAYED.getCode()) {
            return HttpStatus.CONFLICT;
        }
        // 签名缺失/无效/超时（4013-4015）刻意归到 400 而不是 401：
        // 401 会被前端当成"登录态失效"直接踢回登录页，而签名错误通常是客户端漏加签名头，
        // 让用户重新登录并不能解决问题，只会掩盖真实原因。
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 请求体参数校验失败（@Valid 或 @Validated）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.failed(ResultCode.PARAM_INVALID, msg);
    }

    /**
     * 表单/查询参数绑定失败。
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.failed(ResultCode.PARAM_INVALID, msg);
    }

    /**
     * 单个参数校验失败（@RequestParam + @NotBlank 等）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        return R.failed(ResultCode.PARAM_INVALID, e.getMessage());
    }

    /**
     * 请求路径没有对应的处理器/静态资源（Spring Boot 3.2 起统一抛该异常）。
     * <p>
     * 必须在兜底之前单独处理：否则「路径打错」「爬虫扫不存在的路径」这类完全正常的 404
     * 会落进 {@link #handleException(Exception)}，被记成 ERROR 级"未知异常"并返回 500
     * —— 既让调用方把「资源不存在」误判为服务故障，也会用无意义的堆栈污染错误日志
     * （探活/监控误打不存在的路径时尤其明显）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求路径不存在: {}", e.getResourcePath());
        return R.failed(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底：未知异常。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return R.failed(ResultCode.SERVER_ERROR, "系统繁忙，请稍后重试");
    }
}
