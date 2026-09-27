package com.shumamall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.order.dto.TopProductVO;
import com.shumamall.order.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细 Mapper。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {

    /**
     * 根据订单ID查询所有明细。
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id ASC")
    List<OrderItemEntity> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 热销商品排行：按已支付订单（待发货/待收货/已完成/售后中）明细聚合销售额，降序取 Top N。
     *
     * @param limit 返回条数
     * @return 热销商品列表
     */
    @Select("""
            SELECT oi.product_id AS productId, oi.product_name AS productName,
                   SUM(oi.quantity) AS salesQuantity, SUM(oi.subtotal) AS salesAmount
            FROM order_item oi
            JOIN `order` o ON o.id = oi.order_id
            WHERE o.status IN (1, 2, 3, 5)
            GROUP BY oi.product_id, oi.product_name
            ORDER BY salesAmount DESC
            LIMIT #{limit}
            """)
    List<TopProductVO> selectTopProducts(@Param("limit") int limit);
}
