package com.shumamall.search.dto;

import lombok.Data;

import java.util.List;

/**
 * 分类 DTO（search 服务反序列化分类树）。
 */
@Data
public class CategoryDTO {

    private Long id;

    private String name;

    private Long parentId;

    private List<CategoryDTO> children;
}
