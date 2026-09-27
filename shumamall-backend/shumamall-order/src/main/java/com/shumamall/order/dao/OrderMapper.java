package com.shumamall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.order.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
}
