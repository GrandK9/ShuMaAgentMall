package com.shumamall.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.payment.entity.PaymentRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 支付记录 Mapper。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecordEntity> {

    /**
     * 根据订单编号查询所有支付记录。
     *
     * @param orderNo 订单编号
     * @return 支付记录列表
     */
    @Select("SELECT * FROM payment_record WHERE order_no = #{orderNo}")
    List<PaymentRecordEntity> selectByOrderNo(@Param("orderNo") String orderNo);
}
