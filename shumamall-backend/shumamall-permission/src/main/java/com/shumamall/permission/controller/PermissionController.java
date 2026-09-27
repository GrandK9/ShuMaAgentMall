package com.shumamall.permission.controller;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.R;
import com.shumamall.permission.dto.PermissionDTO;
import com.shumamall.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限点管理控制器。
 */
@Slf4j
@Tag(name = "权限-权限点管理", description = "管理后台权限点的查询、创建、更新与删除")
@RestController
@RequestMapping("/api/v1/admin/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 获取全部权限点列表。
     */
    @GetMapping
    public R<List<PermissionDTO>> listPermissions() {
        log.debug("查询全部权限点");
        return R.ok(permissionService.listPermissions());
    }

    /**
     * 创建权限点。
     */
    @PostMapping
    @RequirePermission("permission:create")
    public R<Long> createPermission(@Valid @RequestBody PermissionDTO dto) {
        log.debug("创建权限点: code={}", dto.getCode());
        Long id = permissionService.createPermission(dto);
        return R.ok(id);
    }

    /**
     * 更新权限点。
     */
    @PutMapping("/{id}")
    @RequirePermission("permission:edit")
    public R<Void> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionDTO dto) {
        dto.setId(id);
        log.debug("更新权限点: id={}", id);
        permissionService.updatePermission(dto);
        return R.ok();
    }

    /**
     * 删除权限点。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("permission:delete")
    public R<Void> deletePermission(@PathVariable Long id) {
        log.debug("删除权限点: id={}", id);
        permissionService.deletePermission(id);
        return R.ok();
    }
}
