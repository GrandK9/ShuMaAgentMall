package com.shumamall.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import com.shumamall.permission.dao.RoleMapper;
import com.shumamall.permission.dao.RolePermissionMapper;
import com.shumamall.permission.dto.PageQuery;
import com.shumamall.permission.dto.RoleDTO;
import com.shumamall.permission.entity.RoleEntity;
import com.shumamall.permission.entity.RolePermissionEntity;
import com.shumamall.permission.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public PageResult<RoleDTO> listRoles(PageQuery query) {
        Page<RoleEntity> pageParam = new Page<>(query.getPage(), query.getSize());
        IPage<RoleEntity> pageResult = roleMapper.selectPage(pageParam, null);

        List<RoleDTO> records = pageResult.getRecords().stream()
                .map(entity -> {
                    RoleDTO dto = new RoleDTO();
                    BeanUtils.copyProperties(entity, dto);
                    return dto;
                })
                .collect(Collectors.toList());

        return new PageResult<>(pageResult.getCurrent(), pageResult.getSize(),
                pageResult.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleDTO dto) {
        // 检查角色编码是否已存在
        Long exists = roleMapper.selectCount(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getCode, dto.getCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已存在");
        }

        RoleEntity entity = new RoleEntity();
        BeanUtils.copyProperties(dto, entity);
        roleMapper.insert(entity);
        Long roleId = entity.getId();

        // 绑定权限
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            bindPermissions(roleId, dto.getPermissionIds());
        }

        log.info("角色创建: id={}, code={}", roleId, dto.getCode());
        return roleId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleDTO dto) {
        RoleEntity entity = roleMapper.selectById(dto.getId());
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }

        // 检查编码是否被其他角色占用
        if (dto.getCode() != null) {
            Long exists = roleMapper.selectCount(
                    new LambdaQueryWrapper<RoleEntity>()
                            .eq(RoleEntity::getCode, dto.getCode())
                            .ne(RoleEntity::getId, dto.getId()));
            if (exists != null && exists > 0) {
                throw new BusinessException(ResultCode.CONFLICT, "角色编码已被其他角色使用");
            }
        }

        BeanUtils.copyProperties(dto, entity);
        roleMapper.updateById(entity);

        // 重新绑定权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionEntity>()
                        .eq(RolePermissionEntity::getRoleId, dto.getId()));
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            bindPermissions(dto.getId(), dto.getPermissionIds());
        }

        log.info("角色更新: id={}", dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }

        // 删除角色权限关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionEntity>()
                        .eq(RolePermissionEntity::getRoleId, id));

        // 删除角色
        roleMapper.deleteById(id);

        log.info("角色删除: id={}, code={}", id, entity.getCode());
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        List<RolePermissionEntity> rpList = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionEntity>()
                        .eq(RolePermissionEntity::getRoleId, roleId));
        return rpList.stream()
                .map(RolePermissionEntity::getPermissionId)
                .collect(Collectors.toList());
    }

    /**
     * 批量绑定权限到角色。
     */
    private void bindPermissions(Long roleId, List<Long> permissionIds) {
        List<RolePermissionEntity> list = permissionIds.stream()
                .map(permId -> {
                    RolePermissionEntity rp = new RolePermissionEntity();
                    rp.setRoleId(roleId);
                    rp.setPermissionId(permId);
                    return rp;
                })
                .collect(Collectors.toList());
        list.forEach(rolePermissionMapper::insert);
    }
}
