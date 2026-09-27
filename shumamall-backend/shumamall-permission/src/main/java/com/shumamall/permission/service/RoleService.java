package com.shumamall.permission.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.permission.dto.PageQuery;
import com.shumamall.permission.dto.RoleDTO;

import java.util.List;

/**
 * 角色服务接口。
 */
public interface RoleService {

    /**
     * 分页查询角色列表。
     *
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<RoleDTO> listRoles(PageQuery query);

    /**
     * 创建角色（含权限绑定）。
     *
     * @param dto 角色数据，包含关联权限 ID 列表
     * @return 新角色 ID
     */
    Long createRole(RoleDTO dto);

    /**
     * 更新角色及其权限绑定。
     */
    void updateRole(RoleDTO dto);

    /**
     * 删除角色及其权限绑定。
     *
     * @param id 角色 ID
     */
    void deleteRole(Long id);

    /**
     * 获取角色已绑定的权限 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<Long> getRolePermissionIds(Long roleId);
}
