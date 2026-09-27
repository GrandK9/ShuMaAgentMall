package com.shumamall.search.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 DTO（search 服务反序列化 product 服务返回的商品数据）。
 */
@Data
public class ProductDTO {

    private Long id;

    private Long categoryId;

    private Long brandId;

    private String name;

    private String subtitle;

    private String description;

    private String mainImage;

    private BigDecimal price;

    private Integer stock;

    private Integer salesVolume;

    /** 状态 0-下架 1-上架 */
    private Integer status;

    private Integer isNew;

    private Integer isHot;

    private Integer isRecommend;

    private LocalDateTime createdAt;

    private List<Object> skuList;
}
