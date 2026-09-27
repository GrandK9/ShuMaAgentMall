package com.shumamall.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.user.entity.UserAddressEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户地址 Mapper。
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddressEntity> {
}
