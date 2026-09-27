package com.shumamall.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果包装类。
 * <p>
 * 所有 Controller 接口统一返回此类型：
 * <pre>
 *   return R.ok(data);
 *   return R.failed(ResultCode.PARAM_ERROR);
 * </pre>
 *
 * @param <T> 数据泛型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private int code;
    /** 提示信息 */
    private String msg;
    /** 数据载荷 */
    private T data;

    private R() {
    }

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 成功 ====================

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    // ==================== 失败 ====================

    public static <T> R<T> failed(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    public static <T> R<T> failed(ResultCode resultCode, String msg) {
        return new R<>(resultCode.getCode(), msg, null);
    }

    public static <T> R<T> failed(int code, String msg) {
        return new R<>(code, msg, null);
    }

    // ==================== 便捷判断 ====================

    public boolean isSuccess() {
        return code == ResultCode.SUCCESS.getCode();
    }
}
