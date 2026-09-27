package com.shumamall.permission.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.permission.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点 Mapper。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {
}
