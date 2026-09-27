package com.shumamall.product.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.product.dao.BrandMapper;
import com.shumamall.product.dao.CategoryMapper;
import com.shumamall.product.dao.ProductMapper;
import com.shumamall.product.dao.ProductSkuMapper;
import com.shumamall.product.dto.ProductDTO;
import com.shumamall.product.entity.ProductEntity;
import com.shumamall.product.entity.ProductSkuEntity;
import com.shumamall.product.event.ProductEventPublisher;
import com.shumamall.product.service.ProductCacheService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品服务核心路径单元测试（仅编译验证，不依赖 Spring 上下文）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>SKU 库存扣减的防超卖校验（本地库存不足 / 并发条件更新失败）</li>
 *   <li>销量回写（忽略 0 增量 / 回写后失效缓存）</li>
 *   <li>写路径缓存失效（更新 / 删除后必须清掉详情与默认列表缓存）</li>
 * </ul>
 */
class ProductServiceImplTest {

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductSkuMapper productSkuMapper = mock(ProductSkuMapper.class);
    private final BrandMapper brandMapper = mock(BrandMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
    private final ProductCacheService productCacheService = mock(ProductCacheService.class);

    private final ProductServiceImpl service = new ProductServiceImpl(productMapper, productSkuMapper,
            brandMapper, categoryMapper, eventPublisher, productCacheService);

    private ProductSkuEntity sku(Long id, Long productId, int stock) {
        ProductSkuEntity sku = new ProductSkuEntity();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setStock(stock);
        return sku;
    }

    // ==================== deductStock 防超卖 ====================

    @Test
    void deductStock_success_whenStockEnough() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku(1L, 100L, 10));
        when(productSkuMapper.update(isNull(), any())).thenReturn(1);

        service.deductStock(1L, 3);

        // SKU 库存变化后必须失效商品缓存，保证详情页库存一致
        verify(productCacheService).evict(100L);
    }

    @Test
    void deductStock_throws_whenLocalStockInsufficient() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku(1L, 100L, 2));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deductStock(1L, 5));

        assertEquals(ResultCode.STOCK_INSUFFICIENT.getCode(), ex.getCode());
        verify(productSkuMapper, never()).update(isNull(), any());
    }

    @Test
    void deductStock_throws_whenConcurrentUpdateMisses() {
        // 并发下条件更新 stock >= quantity 未命中 → 不允许超卖
        when(productSkuMapper.selectById(1L)).thenReturn(sku(1L, 100L, 10));
        when(productSkuMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.deductStock(1L, 3));
    }

    @Test
    void deductStock_throws_whenSkuNotFound() {
        when(productSkuMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deductStock(999L, 1));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== updateSalesVolume 销量回写 ====================

    @Test
    void updateSalesVolume_ignoresZeroDelta() {
        service.updateSalesVolume(100L, 0);

        verify(productMapper, never()).update(any(), any());
        verify(productCacheService, never()).evict(any());
    }

    @Test
    void updateSalesVolume_updatesAndEvictsCache() {
        service.updateSalesVolume(100L, 5);

        verify(productMapper).update(isNull(), any());
        verify(productCacheService).evict(100L);
    }

    // ==================== 写路径缓存失效 ====================

    @Test
    void update_evictsProductCache() {
        when(productMapper.selectById(100L)).thenReturn(product(100L));
        ProductDTO dto = new ProductDTO();
        dto.setId(100L);

        service.update(dto);

        verify(productCacheService).evict(100L);
    }

    @Test
    void delete_evictsProductCache() {
        when(productMapper.selectById(100L)).thenReturn(product(100L));

        service.delete(100L);

        verify(productCacheService).evict(100L);
    }

    @Test
    void updateSkuStock_evictsOwningProductCache() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku(1L, 100L, 10));

        service.updateSkuStock(1L, 20);

        // 失效的应是 SKU 所属商品，而非 SKU 自身 ID
        verify(productCacheService).evict(100L);
    }

    private ProductEntity product(Long id) {
        ProductEntity entity = new ProductEntity();
        entity.setId(id);
        return entity;
    }
}
