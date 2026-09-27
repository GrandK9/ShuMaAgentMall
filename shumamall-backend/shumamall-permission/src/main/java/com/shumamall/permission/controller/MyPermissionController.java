package com.shumamall.permission.controller;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.permission.service.PermissionCacheService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 当前用户权限控制器。
 * <p>
 * 与 {@link MenuController} 的区别：{@code /permission/menus} 只返回 {@code type=0 MENU}
 * 权限点用于渲染导航，本接口返回当前管理员的**全部**权限编码（含 {@code type=1 BUTTON}），
 * 供前端做按钮级权限控制（{@code v-permission}）。
 */
@Slf4j
@Tag(name = "权限-我的权限", description = "查询当前管理员的全部权限编码，供前端按钮级权限控制使用")
@RestController
@RequestMapping("/permission/my-permissions")
@RequiredArgsConstructor
public class MyPermissionController {

    private final PermissionCacheService permissionCacheService;

    /**
     * 获取当前管理员的全部权限编码。
     * <p>
     * 用户 ID 由 {@code TokenFilter} 解析 JWT 后写入 {@link SecurityContext}；
     * 权限编码走 Redis 缓存（key={@code perm:user:{userId}}），未命中自动回查 DB 并回填。
     *
     * @return 权限编码集合，如 {@code ["product:view", "product:edit"]}
     */
    @GetMapping
    public R<Set<String>> getMyPermissions() {
        Long userId = SecurityContext.getUserId();
        log.debug("查询当前用户权限编码集: userId={}", userId);
        return R.ok(permissionCacheService.getCachedPermissions(userId));
    }
}
