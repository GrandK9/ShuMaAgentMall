package com.shumamall.agent.feign;

import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端（shumamall-product 用户端接口，无需鉴权）。
 */
@FeignClient(name = "shumamall-product", path = "/api/v1/user/product")
public interface ProductFeignClient {

    /**
     * 分页查询商品列表（仅已上架）。
     */
    @GetMapping
    R<PageResult<ProductItemDTO>> list(@RequestParam("page") Integer page,
                                       @RequestParam("size") Integer size,
                                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                                       @RequestParam(value = "brandId", required = false) Long brandId,
                                       @RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestParam(value = "status", required = false) Integer status,
                                       @RequestParam(value = "isHot", required = false) Integer isHot,
                                       @RequestParam(value = "isNew", required = false) Integer isNew,
                                       @RequestParam(value = "isRecommend", required = false) Integer isRecommend,
                                       @RequestParam(value = "sortBy", required = false) String sortBy,
                                       @RequestParam(value = "sortOrder", required = false) String sortOrder);

    /**
     * 查询商品详情。
     */
    @GetMapping("/{id}")
    R<ProductItemDTO> detail(@PathVariable("id") Long id);
}
