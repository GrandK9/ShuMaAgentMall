package com.shumamall.common.perm.audit;

/**
 * 审计记录的 {@code result} 取值。
 * <p>
 * 只有三种终态，不会出现「校验通过但结果未知」的中间态：切面是环绕通知，
 * 方法正常返回即 SUCCESS、抛异常即 FAILED，两者互斥。
 */
public final class AuditResult {

    /** 权限校验通过且方法正常返回 */
    public static final String SUCCESS = "SUCCESS";

    /** 权限校验未通过（无用户上下文 / 无权限 / permission 服务不可用），方法未执行 */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** 权限校验通过但方法执行抛异常（业务失败，如状态机不允许、参数非法） */
    public static final String FAILED = "FAILED";

    private AuditResult() {
    }
}
