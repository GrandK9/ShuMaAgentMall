package com.shumamall.product.comment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.product.comment.entity.ProductCommentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCommentMapper extends BaseMapper<ProductCommentEntity> {
}
