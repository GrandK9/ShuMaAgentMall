package com.shumamall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.order.entity.CartEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 购物车 Mapper。
 */
@Mapper
public interface CartMapper extends BaseMapper<CartEntity> {

    /**
     * 根据用户ID和SKU ID查询购物车记录。
     *
     * @param userId 用户ID
     * @param skuId  SKU ID
     * @return 购物车记录，未找到返回 null
     */
    @Select("SELECT * FROM cart WHERE user_id = #{userId} AND sku_id = #{skuId} LIMIT 1")
    CartEntity selectByUserIdAndSkuId(@Param("userId") Long userId, @Param("skuId") Long skuId);
}
