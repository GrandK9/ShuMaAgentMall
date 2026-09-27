package com.shumamall.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 DTO（管理端请求/响应通用）。
 */
@Data
public class ProductDTO {

    private Long id;

    /** 分类ID */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** 品牌ID */
    private Long brandId;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 商品详情(HTML) */
    private String description;

    /** 主图URL */
    private String mainImage;

    /** 默认价格 */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
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

    /** SKU列表 */
    private List<ProductSkuDTO> skuList;

    /** 创建时间（查询/展示用） */
    private LocalDateTime createdAt;
}
