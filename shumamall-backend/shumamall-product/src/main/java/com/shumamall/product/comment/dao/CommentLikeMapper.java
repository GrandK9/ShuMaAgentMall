package com.shumamall.product.comment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shumamall.product.comment.entity.CommentLikeEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLikeEntity> {
}
