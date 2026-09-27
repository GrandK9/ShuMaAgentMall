package com.shumamall.product.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.product.dto.BrandDTO;
import com.shumamall.product.dto.PageQuery;

/**
 * 品牌服务接口。
 */
public interface BrandService {

    /**
     * 分页查询品牌列表。
     *
     * @param query 分页查询参数
     * @return 品牌分页结果
     */
    PageResult<BrandDTO> list(PageQuery query);

    /**
     * 创建品牌。
     *
     * @param brandDTO 品牌信息
     * @return 品牌ID
     */
    Long create(BrandDTO brandDTO);

    /**
     * 更新品牌。
     *
     * @param brandDTO 品牌信息
     */
    void update(BrandDTO brandDTO);

    /**
     * 删除品牌。
     *
     * @param id 品牌ID
     */
    void delete(Long id);
}
