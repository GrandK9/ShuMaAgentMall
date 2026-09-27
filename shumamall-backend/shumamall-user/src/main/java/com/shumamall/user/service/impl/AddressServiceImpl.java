package com.shumamall.user.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.user.dao.UserAddressMapper;
import com.shumamall.user.dto.AddressDTO;
import com.shumamall.user.entity.UserAddressEntity;
import com.shumamall.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    @Override
    public List<AddressDTO> listAddresses(Long userId) {
        List<UserAddressEntity> list = addressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAddressEntity>()
                        .eq(UserAddressEntity::getUserId, userId)
                        .orderByDesc(UserAddressEntity::getIsDefault)
                        .orderByDesc(UserAddressEntity::getCreatedAt));
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addAddress(Long userId, AddressDTO dto) {
        // 如果是默认地址，先清除其他默认
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
        }

        UserAddressEntity entity = new UserAddressEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setUserId(userId);

        addressMapper.insert(entity);
        log.info("新增收货地址: userId={}, addressId={}", userId, entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Long addressId, AddressDTO dto) {
        UserAddressEntity entity = addressMapper.selectById(addressId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }

        // 如果是默认地址，先清除其他默认
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
        }

        BeanUtils.copyProperties(dto, entity);
        entity.setId(addressId);
        entity.setUserId(userId);

        addressMapper.updateById(entity);
        log.info("更新收货地址: addressId={}", addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        UserAddressEntity entity = addressMapper.selectById(addressId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }

        addressMapper.deleteById(addressId);
        log.info("删除收货地址: addressId={}", addressId);
    }

    @Override
    public AddressDTO getAddressById(Long userId, Long addressId) {
        UserAddressEntity entity = addressMapper.selectById(addressId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return toDTO(entity);
    }

    /**
     * 清除该用户所有默认地址标记。
     */
    private void clearDefault(Long userId) {
        UserAddressEntity update = new UserAddressEntity();
        update.setIsDefault(0);
        addressMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserAddressEntity>()
                        .eq(UserAddressEntity::getUserId, userId)
                        .eq(UserAddressEntity::getIsDefault, 1));
    }

    private AddressDTO toDTO(UserAddressEntity entity) {
        AddressDTO dto = new AddressDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
