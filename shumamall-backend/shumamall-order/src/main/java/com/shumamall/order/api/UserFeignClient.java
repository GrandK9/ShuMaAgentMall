package com.shumamall.order.api;

import com.shumamall.common.dto.AddressSnapshotDTO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 客户端。
 */
@FeignClient("shumamall-user")
public interface UserFeignClient {

    /**
     * 查询地址详情（快照）。
     *
     * @param id     地址ID
     * @param userId 用户ID
     * @return 地址快照
     */
    @GetMapping("/api/v1/internal/user/address/{id}")
    R<AddressSnapshotDTO> getAddressById(@PathVariable("id") Long id, @RequestParam("userId") Long userId);
}
