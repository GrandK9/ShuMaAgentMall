package com.shumamall.permission.api.impl;

import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.result.R;
import com.shumamall.permission.service.AuditLogService;
import com.shumamall.permission.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 权限服务内部控制器。
 * <p>
 * 实现 {@link com.shumamall.permission.api.PermissionApi} 定义的 Feign 接口，
 * 供其他微服务进行权限校验；
 * 另外提供审计日志上报入口（见 {@code AuditLogReporter}）。
 * <p>
 * 本路径不经过 {@code TokenFilter}（见 WebMvcConfig），供服务间无 token 直连调用，
 * 因此不能在此暴露任何「读取敏感数据」或「修改权限」的能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/permission/internal")
@RequiredArgsConstructor
public class InternalPermissionController {

    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;

    /**
     * 获取用户的所有权限编码（走缓存）。
     */
    @GetMapping("/getUserPermissions")
    public Set<String> getUserPermissions(@RequestParam Long userId) {
        log.debug("内部调用 - 获取用户权限: userId={}", userId);
        return permissionCacheService.getCachedPermissions(userId);
    }

    /**
     * 校验用户是否拥有指定权限。
     */
    @GetMapping("/checkPermission")
    public Boolean checkPermission(@RequestParam Long userId,
                                   @RequestParam String permissionCode) {
        log.debug("内部调用 - 校验权限: userId={}, permissionCode={}", userId, permissionCode);
        Set<String> permissions = permissionCacheService.getCachedPermissions(userId);
        return permissions.contains(permissionCode);
    }

    /**
     * 审计日志上报入口（各业务服务 {@code PermissionAspect} 调用）。
     * <p>
     * 写入失败会抛出异常，由调用方（异步上报线程）捕获并降级为 WARN 日志——
     * 不在本接口内吞异常，是为了让「审计是否真的落库了」在上报侧可见。
     *
     * @param dto 审计记录
     * @return 操作结果
     */
    @PostMapping("/audit")
    public R<Void> reportAudit(@RequestBody AuditLogDTO dto) {
        auditLogService.save(dto);
        return R.ok();
    }
}
