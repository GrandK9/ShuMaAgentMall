package com.shumamall.search.dto;

import lombok.Data;

/**
 * 品牌 DTO（search 服务反序列化品牌列表）。
 */
@Data
public class BrandDTO {

    private Long id;

    private String name;
}
