package com.shumamall.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.permission.dao.AdminUserRoleMapper;
import com.shumamall.permission.dao.PermissionMapper;
import com.shumamall.permission.dao.RoleMapper;
import com.shumamall.permission.dao.RolePermissionMapper;
import com.shumamall.permission.dto.MenuVO;
import com.shumamall.permission.dto.PermissionDTO;
import com.shumamall.permission.entity.AdminUserRoleEntity;
import com.shumamall.permission.entity.PermissionEntity;
import com.shumamall.permission.entity.RoleEntity;
import com.shumamall.permission.entity.RolePermissionEntity;
import com.shumamall.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限点服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    @Override
    public List<PermissionDTO> listPermissions() {
        List<PermissionEntity> entities = permissionMapper.selectList(
                new LambdaQueryWrapper<PermissionEntity>()
                        .orderByAsc(PermissionEntity::getSortOrder)
                        .orderByAsc(PermissionEntity::getId));
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermission(PermissionDTO dto) {
        PermissionEntity entity = new PermissionEntity();
        BeanUtils.copyProperties(dto, entity);
        permissionMapper.insert(entity);
        log.info("权限点创建: id={}, code={}", entity.getId(), entity.getCode());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(PermissionDTO dto) {
        PermissionEntity entity = permissionMapper.selectById(dto.getId());
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限点不存在");
        }
        BeanUtils.copyProperties(dto, entity);
        permissionMapper.updateById(entity);
        log.info("权限点更新: id={}, code={}", entity.getId(), entity.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        PermissionEntity entity = permissionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限点不存在");
        }

        // 检查是否有子节点
        Long childCount = permissionMapper.selectCount(
                new LambdaQueryWrapper<PermissionEntity>()
                        .eq(PermissionEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "存在子权限点，请先删除子节点");
        }

        permissionMapper.deleteById(id);
        log.info("权限点删除: id={}, code={}", id, entity.getCode());
    }

    @Override
    public List<MenuVO> getMenuTree(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 根据角色编码查询角色
        List<RoleEntity> roles = roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getCode, roleCodes)
                        .eq(RoleEntity::getStatus, 1));
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = roles.stream().map(RoleEntity::getId).collect(Collectors.toList());

        // 2. 查询角色关联的权限 ID
        List<RolePermissionEntity> rpList = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionEntity>()
                        .in(RolePermissionEntity::getRoleId, roleIds));
        if (rpList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> permissionIds = rpList.stream()
                .map(RolePermissionEntity::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 查询菜单类型的权限点（type = 0）
        List<PermissionEntity> menuEntities = permissionMapper.selectList(
                new LambdaQueryWrapper<PermissionEntity>()
                        .in(PermissionEntity::getId, permissionIds)
                        .eq(PermissionEntity::getType, 0)
                        .eq(PermissionEntity::getStatus, 1)
                        .orderByAsc(PermissionEntity::getSortOrder)
                        .orderByAsc(PermissionEntity::getId));

        if (menuEntities.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 构建菜单树
        return buildMenuTree(menuEntities);
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        List<AdminUserRoleEntity> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRoleEntity>()
                        .eq(AdminUserRoleEntity::getAdminUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRoleEntity::getRoleId)
                .collect(Collectors.toList());

        List<RoleEntity> roles = roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, roleIds)
                        .eq(RoleEntity::getStatus, 1));

        return roles.stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getUserPermissionCodes(Long userId) {
        // 1. 查询用户关联的角色
        List<AdminUserRoleEntity> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRoleEntity>()
                        .eq(AdminUserRoleEntity::getAdminUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRoleEntity::getRoleId)
                .collect(Collectors.toList());

        // 2. 查询角色关联的权限 ID
        List<RolePermissionEntity> rpList = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionEntity>()
                        .in(RolePermissionEntity::getRoleId, roleIds));
        if (rpList.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> permissionIds = rpList.stream()
                .map(RolePermissionEntity::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 查询权限编码
        List<PermissionEntity> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<PermissionEntity>()
                        .in(PermissionEntity::getId, permissionIds)
                        .eq(PermissionEntity::getStatus, 1));

        return permissions.stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());
    }

    /**
     * 将扁平权限列表构建为菜单树。
     */
    private List<MenuVO> buildMenuTree(List<PermissionEntity> entities) {
        Map<Long, List<PermissionEntity>> parentIdMap = entities.stream()
                .collect(Collectors.groupingBy(e -> e.getParentId() == null ? 0L : e.getParentId()));

        List<MenuVO> roots = new ArrayList<>();
        for (PermissionEntity entity : entities) {
            Long parentId = entity.getParentId() == null ? 0L : entity.getParentId();
            if (parentId == 0L) {
                roots.add(buildMenuNode(entity, parentIdMap));
            }
        }
        return roots;
    }

    private MenuVO buildMenuNode(PermissionEntity entity, Map<Long, List<PermissionEntity>> parentIdMap) {
        MenuVO node = new MenuVO();
        node.setId(entity.getId());
        node.setCode(entity.getCode());
        node.setName(entity.getName());
        node.setPath(entity.getPathPrefix());

        List<PermissionEntity> children = parentIdMap.get(entity.getId());
        if (children != null && !children.isEmpty()) {
            node.setChildren(children.stream()
                    .map(child -> buildMenuNode(child, parentIdMap))
                    .collect(Collectors.toList()));
        }

        return node;
    }

    private PermissionDTO toDTO(PermissionEntity entity) {
        PermissionDTO dto = new PermissionDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
