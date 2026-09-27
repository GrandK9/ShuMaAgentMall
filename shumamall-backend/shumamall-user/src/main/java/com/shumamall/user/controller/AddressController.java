package com.shumamall.user.controller;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.user.dto.AddressDTO;
import com.shumamall.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址控制器。
 */
@Tag(name = "用户-收货地址", description = "当前登录用户收货地址的增删改查接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 查询用户的所有收货地址。
     */
    @Operation(summary = "查询用户的所有收货地址")
    @GetMapping
    public R<List<AddressDTO>> listAddresses() {
        Long userId = SecurityContext.getUserId();
        log.debug("查询地址列表: userId={}", userId);
        return R.ok(addressService.listAddresses(userId));
    }

    /**
     * 新增收货地址。
     */
    @Operation(summary = "新增收货地址")
    @PostMapping
    public R<Long> addAddress(@Valid @RequestBody AddressDTO dto) {
        Long userId = SecurityContext.getUserId();
        log.debug("新增地址: userId={}", userId);
        Long addressId = addressService.addAddress(userId, dto);
        return R.ok(addressId);
    }

    /**
     * 更新收货地址。
     */
    @Operation(summary = "更新收货地址")
    @PutMapping("/{id}")
    public R<Void> updateAddress(@Parameter(description = "地址 ID") @PathVariable Long id,
                                 @Valid @RequestBody AddressDTO dto) {
        Long userId = SecurityContext.getUserId();
        log.debug("更新地址: addressId={}, userId={}", id, userId);
        addressService.updateAddress(userId, id, dto);
        return R.ok();
    }

    /**
     * 删除收货地址。
     */
    @Operation(summary = "删除收货地址")
    @DeleteMapping("/{id}")
    public R<Void> deleteAddress(@Parameter(description = "地址 ID") @PathVariable Long id) {
        Long userId = SecurityContext.getUserId();
        log.debug("删除地址: addressId={}, userId={}", id, userId);
        addressService.deleteAddress(userId, id);
        return R.ok();
    }
}
