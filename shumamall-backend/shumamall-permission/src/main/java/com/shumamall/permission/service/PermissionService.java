package com.shumamall.permission.service;

import com.shumamall.permission.dto.MenuVO;
import com.shumamall.permission.dto.PermissionDTO;

import java.util.List;
import java.util.Set;

/**
 * 权限点服务接口。
 */
public interface PermissionService {

    /**
     * 查询全部权限点列表。
     */
    List<PermissionDTO> listPermissions();

    /**
     * 创建权限点。
     *
     * @param dto 权限点数据
     * @return 新权限点 ID
     */
    Long createPermission(PermissionDTO dto);

    /**
     * 更新权限点。
     */
    void updatePermission(PermissionDTO dto);

    /**
     * 删除权限点。
     *
     * @param id 权限点 ID
     */
    void deletePermission(Long id);

    /**
     * 根据角色编码列表获取菜单树。
     *
     * @param roleCodes 角色编码列表
     * @return 菜单树根节点列表
     */
    List<MenuVO> getMenuTree(List<String> roleCodes);

    /**
     * 获取用户拥有的全部权限编码。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    Set<String> getUserPermissionCodes(Long userId);

    /**
     * 获取用户关联的角色编码列表。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> getUserRoleCodes(Long userId);
}
