package com.shumamall.permission.api;

import com.shumamall.permission.service.PermissionCacheService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * 权限服务 Feign 接口。
 * <p>
 * 供其他微服务（如 shumamall-auth）调用，用于权限校验和缓存查询。
 */
@FeignClient(name = "shumamall-permission", path = "/api/permission/internal")
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
