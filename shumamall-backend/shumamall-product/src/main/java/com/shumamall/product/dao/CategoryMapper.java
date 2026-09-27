package com.shumamall.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.product.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品分类 Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
