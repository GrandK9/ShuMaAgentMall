package com.shumamall.user.service;

import com.shumamall.user.dto.AddressDTO;

import java.util.List;

/**
 * 收货地址服务接口。
 */
public interface AddressService {

    /**
     * 查询用户的所有收货地址。
     */
    List<AddressDTO> listAddresses(Long userId);

    /**
     * 新增收货地址。
     *
     * @return 新地址 ID
     */
    Long addAddress(Long userId, AddressDTO dto);

    /**
     * 更新收货地址。
     */
    void updateAddress(Long userId, Long addressId, AddressDTO dto);

    /**
     * 删除收货地址。
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 根据地址ID查询地址。
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     * @return 地址DTO，不存在返回 null
     */
    AddressDTO getAddressById(Long userId, Long addressId);
}
