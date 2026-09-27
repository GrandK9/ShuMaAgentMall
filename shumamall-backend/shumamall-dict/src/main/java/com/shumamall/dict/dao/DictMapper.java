package com.shumamall.dict.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.dict.entity.DictEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统字典 Mapper。
 */
@Mapper
public interface DictMapper extends BaseMapper<DictEntity> {
}
