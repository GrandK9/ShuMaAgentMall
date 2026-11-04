package com.shumamall.permission.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.permission.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。
 */
@DS("perm")
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
}
