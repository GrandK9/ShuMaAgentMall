package com.shumamall.admin.feign;

import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 商品服务 Feign 客户端。
 *
 * @author ShuMaMall Team
 */
@FeignClient(name = "shumamall-product", path = "/api/v1/admin/product")
public interface ProductFeignClient {

    /**
     * 查询总商品数。
     *
     * @return 商品总数
     */
    @GetMapping("/count")
    R<Long> countProducts();
}
