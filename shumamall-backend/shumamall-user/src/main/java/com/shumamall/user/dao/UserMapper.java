package com.shumamall.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（user 模块）。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
