package com.shumamall.search.feign;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.search.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务管理端 Feign 客户端（全量灌库/增量同步拉取商品数据）。
 * <p>
 * product 管理端接口无鉴权，可直接调用。
 * <p>
 * 三个 Feign 客户端共用同一服务名 shumamall-product，必须各自指定
 * {@code contextId}，否则 FeignClientSpecification Bean 重名会导致应用启动失败。
 */
@FeignClient(name = "shumamall-product", contextId = "searchProductFeignClient",
        path = "/api/v1/admin/product")
public interface ProductFeignClient {

    /**
     * 分页查询商品（管理端，status 传 null 拉全部含下架）。
     */
    @GetMapping
    R<PageResult<ProductDTO>> list(@RequestParam("page") Integer page,
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
    R<ProductDTO> detail(@PathVariable("id") Long id);
}
