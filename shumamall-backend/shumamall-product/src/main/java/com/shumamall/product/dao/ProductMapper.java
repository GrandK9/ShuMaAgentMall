package com.shumamall.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.product.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
}
