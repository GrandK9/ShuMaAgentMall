package com.shumamall.search.feign;

import com.shumamall.common.result.R;
import com.shumamall.search.dto.CategoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 商品分类 Feign 客户端（灌库时建立分类 id→name 映射）。
 * <p>
 * 必须指定 {@code contextId}，与同一服务名下的其他 Feign 客户端区分。
 */
@FeignClient(name = "shumamall-product", contextId = "searchCategoryFeignClient",
        path = "/api/v1/categories")
public interface CategoryFeignClient {

    /**
     * 获取分类树。
     */
    @GetMapping("/tree")
    R<List<CategoryDTO>> tree();
}
