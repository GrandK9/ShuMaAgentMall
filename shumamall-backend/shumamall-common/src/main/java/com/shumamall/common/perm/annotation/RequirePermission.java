package com.shumamall.common.perm.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解。
 * <p>
 * 标注在 Controller 方法上，AOP 切面在方法执行前校验当前用户是否拥有指定权限。
 * 无权限时抛出 {@link com.shumamall.common.exception.BusinessException}，由全局异常处理器返回 403。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @RequirePermission("product:edit")
 * @PostMapping("/save")
 * public R<Void> save(@RequestBody ProductDTO dto) { ... }
 *
 * @RequirePermission(value = {"product:view", "product:edit"}, logical = Logical.OR)
 * @GetMapping("/list")
 * public R<List<ProductDTO>> list() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 所需权限编码列表。
     */
    String[] value();

    /**
     * 逻辑关系。AND 表示同时拥有所有权限，OR 表示拥有任一即可。
     */
    Logical logical() default Logical.AND;

    /**
     * 逻辑关系枚举。
     */
    enum Logical {
        AND, OR
    }
}
