package com.shumamall.permission.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * IAM 权限内部 HTTP 路径约定（由 {@link com.shumamall.permission.api.impl.InternalPermissionController} 实现）。
 * <p>
 * 本接口不带 {@code @FeignClient}：合并进 IAM 后禁止在同进程注册「调自己」的 Feign。
 * 其它微服务通过 {@link com.shumamall.common.perm.spi.UserPermissionsLookup} 访问（HTTP 或本地实现）。
 */
public interface PermissionApi {

    /**
     * 获取用户的所有权限编码（走缓存）。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    @GetMapping("/getUserPermissions")
    Set<String> getUserPermissions(@RequestParam("userId") Long userId);

    /**
     * 校验用户是否拥有指定权限。
     *
     * @param userId         用户 ID
     * @param permissionCode 权限编码
     * @return true 有权限，false 无权限
     */
    @GetMapping("/checkPermission")
    Boolean checkPermission(@RequestParam("userId") Long userId,
                            @RequestParam("permissionCode") String permissionCode);
}
