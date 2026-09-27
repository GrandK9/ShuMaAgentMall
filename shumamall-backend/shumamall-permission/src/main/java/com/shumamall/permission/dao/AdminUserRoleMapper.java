package com.shumamall.permission.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.permission.entity.AdminUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员-角色关联 Mapper。
 */
@Mapper
public interface AdminUserRoleMapper extends BaseMapper<AdminUserRoleEntity> {
}
