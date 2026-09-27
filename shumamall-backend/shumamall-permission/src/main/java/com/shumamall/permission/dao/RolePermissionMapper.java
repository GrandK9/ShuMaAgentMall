package com.shumamall.permission.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.permission.entity.RolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {
}
