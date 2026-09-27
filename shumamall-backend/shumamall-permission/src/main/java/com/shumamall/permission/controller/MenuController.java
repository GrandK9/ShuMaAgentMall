package com.shumamall.permission.controller;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.permission.dto.MenuVO;
import com.shumamall.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单控制器。
 * <p>
 * 返回当前管理员可用的菜单树，前端用于动态渲染导航菜单。
 */
@Slf4j
@Tag(name = "权限-菜单", description = "查询当前管理员可用的菜单树，供前端动态渲染导航")
@RestController
@RequestMapping("/permission/menus")
@RequiredArgsConstructor
public class MenuController {

    private final PermissionService permissionService;

    /**
     * 获取当前管理员的菜单树。
     * <p>
     * 从 SecurityContext 获取当前用户 ID，查询其角色编码列表，
     * 再根据角色权限过滤出菜单类型的权限点构建树形结构。
     */
    @GetMapping
    public R<List<MenuVO>> getMenuTree() {
        Long userId = SecurityContext.getUserId();
        log.debug("查询菜单树: userId={}", userId);

        // 获取当前用户的角色编码列表
        List<String> roleCodes = permissionService.getUserRoleCodes(userId);
        List<MenuVO> menuTree = permissionService.getMenuTree(roleCodes);
        return R.ok(menuTree);
    }
}
