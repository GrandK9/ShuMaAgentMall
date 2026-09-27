package com.shumamall.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体。
 */
@Data
@TableName("category")
public class CategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类ID, 0为顶级 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 图标URL */
    private String icon;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态 0-隐藏 1-显示 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
