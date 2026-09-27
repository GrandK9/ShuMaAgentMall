package com.shumamall.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.product.entity.ProductSkuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品 SKU Mapper。
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuEntity> {

    /**
     * 根据商品ID查询所有SKU。
     *
     * @param productId 商品ID
     * @return SKU列表
     */
    @Select("SELECT * FROM product_sku WHERE product_id = #{productId} ORDER BY id ASC")
    List<ProductSkuEntity> selectByProductId(@Param("productId") Long productId);
}
