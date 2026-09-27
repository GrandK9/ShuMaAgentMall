package com.shumamall.agent.search.document;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 ES 索引文档模型（shumamall_product）。
 * <p>
 * 与 {@code ProductItemDTO}（Feign 返回的精简 DTO）对应，额外包含
 * 检索所需字段：品牌、分类、描述文本与语义向量（embedding）。
 * <p>
 * 字段命名与 {@code ProductIndexService} 创建的索引 mapping 保持一致，
 * ES 客户端按该 POJO 直接反序列化命中文档。
 */
@Data
public class ProductDoc {

    /** 商品 ID（与 MySQL 商品主键一致） */
    private Long id;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 品牌 */
    private String brand;

    /** 分类 */
    private String category;

    /** 默认价格 */
    private BigDecimal price;

    /** 默认库存 */
    private Integer stock;

    /** 销量 */
    private Integer salesVolume;

    /** 商品详情描述（文本检索字段） */
    private String description;

    /** 语义向量（文本 embedding，维度与索引 mapping 的 dense_vector dims 一致） */
    private List<Float> embedding;
}
