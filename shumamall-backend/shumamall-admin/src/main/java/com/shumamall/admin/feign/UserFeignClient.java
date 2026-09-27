package com.shumamall.admin.feign;

import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户服务 Feign 客户端。
 * <p>
 * 对接 user 服务内部接口（无鉴权，供服务间调用）。
 *
 * @author ShuMaMall Team
 */
@FeignClient(name = "shumamall-user", path = "/api/v1/internal/user")
public interface UserFeignClient {

    /**
     * 查询总用户数。
     *
     * @return 用户总数
     */
    @GetMapping("/count")
    R<Long> countUsers();
}
