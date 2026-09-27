package com.shumamall.product.service;

import com.shumamall.product.dto.CategoryDTO;

import java.util.List;

/**
 * 商品分类服务接口。
 */
public interface CategoryService {

    /**
     * 获取分类树。
     *
     * @return 树形分类列表
     */
    List<CategoryDTO> listTree();

    /**
     * 创建分类。
     *
     * @param categoryDTO 分类信息
     * @return 分类ID
     */
    Long create(CategoryDTO categoryDTO);

    /**
     * 更新分类。
     *
     * @param categoryDTO 分类信息
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 删除分类。
     *
     * @param id 分类ID
     */
    void delete(Long id);
}
