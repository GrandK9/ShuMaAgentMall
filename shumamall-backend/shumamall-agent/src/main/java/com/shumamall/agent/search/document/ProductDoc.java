package com.shumamall.agent.search.document;

import lombok.Data;

import java.util.List;

/**
 * 商品 ES 文档（与 search 服务 {@code shumamall_product_search} 索引字段对齐，供 Hybrid 反序列化）。
 */
@Data
public class ProductDoc {

    private Long id;
    private String name;
    private String subtitle;
    private String description;
    private String categoryName;
    private String brandName;
    private Double price;
    private Integer salesVolume;
    private List<Float> embedding;

    /** 工具/接口展示用，兼容旧字段名 */
    public String getBrand() {
        return brandName;
    }

    public String getCategory() {
        return categoryName;
    }
}
