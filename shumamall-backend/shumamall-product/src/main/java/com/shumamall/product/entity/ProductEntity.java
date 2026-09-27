package com.shumamall.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体。
 */
@Data
@TableName("product")
public class ProductEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类ID */
    private Long categoryId;

    /** 品牌ID */
    private Long brandId;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 商品详情(HTML) */
    private String description;

    /** 主图URL */
    private String mainImage;

    /** 默认价格 */
    private BigDecimal price;

    /** 默认库存 */
    private Integer stock;

    /** 销量 */
    private Integer salesVolume;

    /** 评论数（评论区服务回写） */
    private Integer commentCount;

    /** 状态 0-下架 1-上架 */
    private Integer status;

    /** 是否新品 0-否 1-是 */
    private Integer isNew;

    /** 是否热销 0-否 1-是 */
    private Integer isHot;

    /** 是否推荐 0-否 1-是 */
    private Integer isRecommend;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
