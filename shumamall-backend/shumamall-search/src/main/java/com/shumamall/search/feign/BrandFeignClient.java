package com.shumamall.search.feign;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.search.dto.BrandDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 品牌 Feign 客户端（灌库时建立品牌 id→name 映射）。
 * <p>
 * 必须指定 {@code contextId}，与同一服务名下的其他 Feign 客户端区分。
 */
@FeignClient(name = "shumamall-product", contextId = "searchBrandFeignClient",
        path = "/api/v1/admin/brand")
public interface BrandFeignClient {

    /**
     * 分页查询品牌列表。
     */
    @GetMapping
    R<PageResult<BrandDTO>> list(@RequestParam("page") Integer page,
                                 @RequestParam("size") Integer size);
}
