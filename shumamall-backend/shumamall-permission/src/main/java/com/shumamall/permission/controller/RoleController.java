package com.shumamall.permission.controller;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.permission.dto.PageQuery;
import com.shumamall.permission.dto.RoleDTO;
import com.shumamall.permission.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器。
 */
@Slf4j
@Tag(name = "权限-角色管理", description = "管理后台角色的分页查询、创建、更新、删除及权限绑定查询")
@RestController
@RequestMapping("/api/v1/admin/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 分页查询角色列表。
     */
    @GetMapping
    public R<PageResult<RoleDTO>> listRoles(PageQuery query) {
        log.debug("分页查询角色: page={}, size={}", query.getPage(), query.getSize());
        return R.ok(roleService.listRoles(query));
    }

    /**
     * 创建角色（含权限绑定）。
     */
    @PostMapping
    public R<Long> createRole(@Valid @RequestBody RoleDTO dto) {
        log.debug("创建角色: code={}, name={}", dto.getCode(), dto.getName());
        Long id = roleService.createRole(dto);
        return R.ok(id);
    }

    /**
     * 更新角色及其权限绑定。
     */
    @PutMapping("/{id}")
    public R<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        dto.setId(id);
        log.debug("更新角色: id={}", id);
        roleService.updateRole(dto);
        return R.ok();
    }

    /**
     * 删除角色。
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        log.debug("删除角色: id={}", id);
        roleService.deleteRole(id);
        return R.ok();
    }

    /**
     * 获取角色已绑定的权限 ID 列表。
     */
    @GetMapping("/{roleId}/permissions")
    public R<List<Long>> getRolePermissionIds(@PathVariable Long roleId) {
        log.debug("查询角色权限: roleId={}", roleId);
        return R.ok(roleService.getRolePermissionIds(roleId));
    }
}
