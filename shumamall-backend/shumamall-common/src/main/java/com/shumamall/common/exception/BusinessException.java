package com.shumamall.common.exception;

import com.shumamall.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 在 Service 层抛出，由 GlobalExceptionHandler 统一捕获处理。
 * <pre>
 *   throw new BusinessException(ResultCode.USER_NOT_FOUND);
 *   throw new BusinessException(ResultCode.PARAM_INVALID, "手机号格式错误");
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
