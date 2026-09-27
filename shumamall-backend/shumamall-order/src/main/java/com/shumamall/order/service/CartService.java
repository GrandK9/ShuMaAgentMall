package com.shumamall.order.service;

import com.shumamall.order.dto.CartAddDTO;
import com.shumamall.order.dto.CartDTO;

import java.util.List;

/**
 * 购物车服务接口。
 */
public interface CartService {

    /**
     * 添加商品到购物车。
     *
     * @param userId 用户ID
     * @param dto    添加参数
     */
    void addItem(Long userId, CartAddDTO dto);

    /**
     * 从购物车移除商品。
     *
     * @param userId 用户ID
     * @param skuId  SKU ID
     */
    void removeItem(Long userId, Long skuId);

    /**
     * 更新购物车商品数量。
     *
     * @param userId   用户ID
     * @param skuId    SKU ID
     * @param quantity 新数量
     */
    void updateQuantity(Long userId, Long skuId, Integer quantity);

    /**
     * 更新购物车商品选中状态。
     *
     * @param userId   用户ID
     * @param skuId    SKU ID
     * @param selected 选中状态 0-未选中 1-选中
     */
    void updateSelected(Long userId, Long skuId, Integer selected);

    /**
     * 获取用户的购物车列表。
     *
     * @param userId 用户ID
     * @return 购物车DTO列表
     */
    List<CartDTO> getCartList(Long userId);

    /**
     * 清空用户的购物车。
     *
     * @param userId 用户ID
     */
    void clearCart(Long userId);
}
