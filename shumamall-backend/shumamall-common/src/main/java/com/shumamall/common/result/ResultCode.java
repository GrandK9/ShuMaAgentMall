package com.shumamall.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举。
 * <p>
 * 约定：
 * <ul>
 *   <li>2xxx — 成功</li>
 *   <li>4xxx — 客户端错误（参数/权限/资源不存在）</li>
 *   <li>5xxx — 服务端错误（系统异常）</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ==================== 成功 ====================
    SUCCESS(2000, "操作成功"),

    // ==================== 客户端错误 ====================
    PARAM_ERROR(4000, "参数错误"),
    PARAM_MISSING(4001, "缺少必要参数"),
    PARAM_INVALID(4002, "参数校验失败"),

    UNAUTHORIZED(4010, "未登录或 token 已过期"),
    TOKEN_INVALID(4011, "无效的 token"),
    TOKEN_EXPIRED(4012, "token 已过期"),

    // 请求签名与防重放（详见 com.shumamall.common.sign.RequestSignFilter）
    SIGN_MISSING(4013, "缺少请求签名头"),
    SIGN_INVALID(4014, "请求签名校验失败"),
    SIGN_EXPIRED(4015, "请求时间戳超出允许范围"),
    NONCE_REPLAYED(4016, "请求重复提交"),

    FORBIDDEN(4030, "无权限访问"),
    PERMISSION_DENIED(4031, "权限不足"),

    NOT_FOUND(4040, "资源不存在"),
    USER_NOT_FOUND(4041, "用户不存在"),
    PRODUCT_NOT_FOUND(4042, "商品不存在"),
    ORDER_NOT_FOUND(4043, "订单不存在"),

    CONFLICT(4090, "资源冲突"),
    USER_EXISTS(4091, "用户已存在"),

    UNSUPPORTED_MEDIA_TYPE(4150, "不支持的媒体类型"),

    // ==================== 服务端错误 ====================
    SERVER_ERROR(5000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(5030, "服务暂不可用"),

    // ==================== 业务错误 ====================
    STOCK_INSUFFICIENT(5100, "库存不足"),
    ORDER_STATUS_INVALID(5101, "订单状态不允许此操作"),
    PAYMENT_FAILED(5102, "支付失败"),
    FILE_TOO_LARGE(5103, "文件过大"),
    FILE_FORMAT_INVALID(5104, "文件格式不支持"),
    VIDEO_DURATION_EXCEEDED(5105, "视频时长超限"),
    VIDEO_RESOLUTION_EXCEEDED(5106, "视频分辨率超限"),
    PAY_AMOUNT_MISMATCH(5107, "支付金额与订单应付金额不一致"),
    ORDER_ALREADY_PAID(5108, "订单已支付，请勿重复支付"),
    ;

    private final int code;
    private final String msg;
}
