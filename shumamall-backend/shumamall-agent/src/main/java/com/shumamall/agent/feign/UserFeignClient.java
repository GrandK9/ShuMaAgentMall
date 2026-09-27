package com.shumamall.agent.feign;

import com.shumamall.agent.dto.AddressInfoDTO;
import com.shumamall.agent.dto.UserInfoDTO;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 用户服务 Feign 客户端（shumamall-user 用户端接口，需携带 JWT 透传 userId）。
 */
@FeignClient(name = "shumamall-user")
public interface UserFeignClient {

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/api/v1/user/info")
    R<UserInfoDTO> userInfo();

    /**
     * 获取当前用户收货地址列表。
     */
    @GetMapping("/api/v1/user/address")
    R<List<AddressInfoDTO>> addressList();
}
