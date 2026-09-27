package com.shumamall.order.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.order.seckill.entity.SeckillOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀资格记录 Mapper。
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrderEntity> {
}
