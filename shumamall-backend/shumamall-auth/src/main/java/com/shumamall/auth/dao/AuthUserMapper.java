package com.shumamall.auth.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录认证用用户 Mapper（与 user 模块 {@code UserMapper} 同表，合并进程需独立 Bean 名）。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<UserEntity> {
}
