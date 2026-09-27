package com.shumamall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.order.api.ProductFeignClient;
import com.shumamall.order.dao.CartMapper;
import com.shumamall.order.dto.CartAddDTO;
import com.shumamall.order.dto.CartDTO;
import com.shumamall.order.entity.CartEntity;
import com.shumamall.order.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductFeignClient productFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddDTO dto) {
        // 服务层兜底校验：数量必须为正整数，否则后续扣库存会变成加库存
        requirePositiveQuantity(dto.getQuantity());
        // 使用唯一键（user_id + sku_id），存在则增加数量，不存在则新增
        CartEntity existing = cartMapper.selectByUserIdAndSkuId(userId, dto.getSkuId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + dto.getQuantity());
            existing.setSelected(1);
            cartMapper.updateById(existing);
            log.debug("购物车商品数量更新: userId={}, skuId={}, quantity={}", userId, dto.getSkuId(), existing.getQuantity());
        } else {
            CartEntity cart = new CartEntity();
            cart.setUserId(userId);
            cart.setSkuId(dto.getSkuId());
            cart.setProductId(dto.getProductId());
            cart.setQuantity(dto.getQuantity());
            cart.setSelected(1);
            cartMapper.insert(cart);
            log.debug("购物车新增商品: userId={}, skuId={}", userId, dto.getSkuId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long skuId) {
        LambdaQueryWrapper<CartEntity> wrapper = new LambdaQueryWrapper<CartEntity>()
                .eq(CartEntity::getUserId, userId)
                .eq(CartEntity::getSkuId, skuId);
        int deleted = cartMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车中未找到该商品");
        }
        log.debug("购物车商品已移除: userId={}, skuId={}", userId, skuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long skuId, Integer quantity) {
        requirePositiveQuantity(quantity);
        CartEntity existing = cartMapper.selectByUserIdAndSkuId(userId, skuId);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车中未找到该商品");
        }
        existing.setQuantity(quantity);
        cartMapper.updateById(existing);
        log.debug("购物车数量更新: userId={}, skuId={}, quantity={}", userId, skuId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelected(Long userId, Long skuId, Integer selected) {
        CartEntity existing = cartMapper.selectByUserIdAndSkuId(userId, skuId);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车中未找到该商品");
        }
        existing.setSelected(selected);
        cartMapper.updateById(existing);
        log.debug("购物车选中状态更新: userId={}, skuId={}, selected={}", userId, skuId, selected);
    }

    @Override
    public List<CartDTO> getCartList(Long userId) {
        LambdaQueryWrapper<CartEntity> wrapper = new LambdaQueryWrapper<CartEntity>()
                .eq(CartEntity::getUserId, userId)
                .orderByDesc(CartEntity::getCreatedAt);

        List<CartEntity> list = cartMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有 SKU ID，批量查询商品信息
        List<Long> skuIds = list.stream().map(CartEntity::getSkuId).collect(Collectors.toList());
        Map<Long, SkuInfoDTO> skuInfoMap = Collections.emptyMap();
        try {
            R<List<SkuInfoDTO>> result = productFeignClient.getSkuListByIds(skuIds);
            if (result != null && result.isSuccess() && result.getData() != null) {
                skuInfoMap = result.getData().stream()
                        .collect(Collectors.toMap(SkuInfoDTO::getSkuId, s -> s));
            }
        } catch (Exception e) {
            log.error("批量查询SKU信息失败: skuIds={}", skuIds, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "商品服务调用失败");
        }

        final Map<Long, SkuInfoDTO> finalMap = skuInfoMap;
        return list.stream().map(item -> toCartDTO(item, finalMap.get(item.getSkuId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        LambdaQueryWrapper<CartEntity> wrapper = new LambdaQueryWrapper<CartEntity>()
                .eq(CartEntity::getUserId, userId);
        int deleted = cartMapper.delete(wrapper);
        log.debug("购物车已清空: userId={}, 删除条数={}", userId, deleted);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验购物车数量为正整数。
     * <p>
     * 负数会穿透库存条件（stock >= -n 恒成立），并让 stock = stock - (-n) 变成加库存。
     *
     * @param quantity 待校验数量
     */
    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "数量必须大于 0");
        }
    }

    /**
     * 将购物车实体转换为DTO，回填商品信息。
     *
     * @param entity   购物车实体
     * @param skuInfo  SKU 信息（可能为 null）
     * @return 购物车DTO
     */
    private CartDTO toCartDTO(CartEntity entity, SkuInfoDTO skuInfo) {
        CartDTO dto = new CartDTO();
        dto.setId(entity.getId());
        dto.setSkuId(entity.getSkuId());
        dto.setProductId(entity.getProductId());
        dto.setQuantity(entity.getQuantity());
        dto.setSelected(entity.getSelected());

        if (skuInfo != null) {
            dto.setProductName(skuInfo.getProductName());
            dto.setProductImage(skuInfo.getProductImage());
            dto.setSkuSpecs(skuInfo.getSkuSpecs());
            dto.setPrice(skuInfo.getPrice());
            dto.setSubtotal(skuInfo.getPrice().multiply(BigDecimal.valueOf(entity.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setSubtotal(BigDecimal.ZERO);
        }

        return dto;
    }
}
