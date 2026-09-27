package com.shumamall.user.controller.internal;

import com.shumamall.common.dto.AddressSnapshotDTO;
import com.shumamall.common.result.R;
import com.shumamall.user.dto.AddressDTO;
import com.shumamall.user.service.AddressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 收货地址内部控制器（仅服务间 Feign 调用）。
 */
@Slf4j
@Tag(name = "内部接口-收货地址", description = "微服务间 Feign 调用的收货地址快照查询接口")
@RestController
@RequestMapping("/api/v1/internal/user/address")
@RequiredArgsConstructor
public class AddressInternalController {

    private final AddressService addressService;

    /**
     * 根据 ID 和用户 ID 查询地址快照。
     *
     * @param id     地址ID
     * @param userId 用户ID
     * @return 地址快照
     */
    @GetMapping("/{id}")
    public R<AddressSnapshotDTO> getAddressById(@PathVariable Long id, @RequestParam Long userId) {
        log.debug("内部查询地址快照: addressId={}, userId={}", id, userId);
        AddressDTO dto = addressService.getAddressById(userId, id);
        AddressSnapshotDTO snapshot = new AddressSnapshotDTO();
        BeanUtils.copyProperties(dto, snapshot);
        return R.ok(snapshot);
    }
}
