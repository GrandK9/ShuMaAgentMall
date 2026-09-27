package com.shumamall.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌实体。
 */
@Data
@TableName("brand")
public class BrandEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌名称 */
    private String name;

    /** 品牌Logo */
    private String logo;

    /** 品牌描述 */
    private String description;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态 0-隐藏 1-显示 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
