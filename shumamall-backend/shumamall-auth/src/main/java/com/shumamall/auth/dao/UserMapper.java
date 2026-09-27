package com.shumamall.auth.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（auth 模块）。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
