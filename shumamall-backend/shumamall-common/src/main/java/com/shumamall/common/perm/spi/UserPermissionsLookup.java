package com.shumamall.common.perm.spi;

import java.util.Set;

/**
 * 解析管理员权限编码（供 {@code PermissionAspect} 使用）。
 * <p>
 * IAM 进程内走本地实现；其它可部署服务走 HTTP 访问 IAM。
 */
public interface UserPermissionsLookup {

    /**
     * @param userId 管理员用户 ID
     * @return 权限编码集合；不可用或失败时返回空集
     */
    Set<String> getPermissions(Long userId);
}
