package com.shumamall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import com.shumamall.product.dao.BrandMapper;
import com.shumamall.product.dao.CategoryMapper;
import com.shumamall.product.dao.ProductMapper;
import com.shumamall.product.dao.ProductSkuMapper;
import com.shumamall.product.dto.ProductDTO;
import com.shumamall.product.dto.ProductPageQuery;
import com.shumamall.product.dto.ProductSkuDTO;
import com.shumamall.product.dto.ProductVO;
import com.shumamall.product.entity.BrandEntity;
import com.shumamall.product.entity.CategoryEntity;
import com.shumamall.product.entity.ProductEntity;
import com.shumamall.product.entity.ProductSkuEntity;
import com.shumamall.product.event.ProductEventPublisher;
import com.shumamall.product.service.ProductCacheService;
import com.shumamall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final BrandMapper brandMapper;
    private final CategoryMapper categoryMapper;
    private final ProductEventPublisher productEventPublisher;
    private final ProductCacheService productCacheService;

    @Override
    public PageResult<ProductDTO> pageQuery(ProductPageQuery query) {
        Page<ProductEntity> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<ProductEntity> wrapper = buildQueryWrapper(query);

        Page<ProductEntity> result = productMapper.selectPage(page, wrapper);

        List<ProductDTO> list = result.getRecords().stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    @Override
    public ProductDTO getById(Long id) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        return toProductDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductDTO productDTO) {
        ProductEntity entity = new ProductEntity();
        BeanUtils.copyProperties(productDTO, entity);

        if (entity.getSalesVolume() == null) {
            entity.setSalesVolume(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        if (entity.getStock() == null) {
            entity.setStock(0);
        }

        productMapper.insert(entity);
        Long productId = entity.getId();

        // 保存 SKU
        if (productDTO.getSkuList() != null && !productDTO.getSkuList().isEmpty()) {
            for (ProductSkuDTO skuDTO : productDTO.getSkuList()) {
                ProductSkuEntity skuEntity = new ProductSkuEntity();
                BeanUtils.copyProperties(skuDTO, skuEntity);
                skuEntity.setProductId(productId);
                if (skuEntity.getStatus() == null) {
                    skuEntity.setStatus(1);
                }
                if (skuEntity.getStock() == null) {
                    skuEntity.setStock(0);
                }
                productSkuMapper.insert(skuEntity);
            }
        }

        log.info("商品创建成功: id={}, name={}", productId, entity.getName());
        // 失效缓存：新建商品可能直接是已上架状态，默认列表需要重建
        productCacheService.evict(productId);
        return productId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ProductDTO productDTO) {
        ProductEntity entity = productMapper.selectById(productDTO.getId());
        if (entity == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在");
        }

        BeanUtils.copyProperties(productDTO, entity);
        productMapper.updateById(entity);

        // 更新 SKU：先删除旧 SKU，再插入新 SKU
        if (productDTO.getSkuList() != null) {
            productSkuMapper.delete(
                    new LambdaQueryWrapper<ProductSkuEntity>()
                            .eq(ProductSkuEntity::getProductId, productDTO.getId()));

            for (ProductSkuDTO skuDTO : productDTO.getSkuList()) {
                ProductSkuEntity skuEntity = new ProductSkuEntity();
                BeanUtils.copyProperties(skuDTO, skuEntity);
                skuEntity.setProductId(productDTO.getId());
                // 清空ID以支持新增
                skuEntity.setId(null);
                if (skuEntity.getStatus() == null) {
                    skuEntity.setStatus(1);
                }
                if (skuEntity.getStock() == null) {
                    skuEntity.setStock(0);
                }
                productSkuMapper.insert(skuEntity);
            }
        }

        log.info("商品更新成功: id={}", entity.getId());
        // 失效缓存：名称/价格/SKU/上下架均可能变化
        productCacheService.evict(entity.getId());
        // 发布事件 → search 服务增量同步索引
        productEventPublisher.publishUpsert(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在");
        }

        entity.setStatus(status);
        productMapper.updateById(entity);
        log.info("商品状态更新: id={}, status={}", id, status);
        // 失效缓存：上下架直接影响用户端可见性
        productCacheService.evict(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在");
        }

        // 删除关联 SKU
        productSkuMapper.delete(
                new LambdaQueryWrapper<ProductSkuEntity>()
                        .eq(ProductSkuEntity::getProductId, id));

        productMapper.deleteById(id);
        log.info("商品删除成功: id={}", id);
        // 失效缓存：删除后详情/列表不应再命中旧数据（含空值标记）
        productCacheService.evict(id);
        // 发布事件 → search 服务增量删除索引
        productEventPublisher.publishDelete(id);
    }

    @Override
    public PageResult<ProductVO> getUserFacingList(ProductPageQuery query) {
        // 用户端只展示已上架商品
        query.setStatus(1);

        // 仅默认列表（首页第一页、无筛选）走缓存，带筛选/翻页场景直接查 DB
        if (isDefaultListQuery(query)) {
            PageResult<ProductVO> cached = productCacheService.getWithCache(
                    ProductCacheService.LIST_KEY,
                    new TypeReference<PageResult<ProductVO>>() {},
                    Duration.ofMinutes(30),
                    () -> queryProductPage(query));
            return cached != null ? cached
                    : new PageResult<>(query.getPage(), query.getSize(), 0, Collections.emptyList());
        }
        return queryProductPage(query);
    }

    @Override
    public ProductVO getUserFacingDetail(Long productId) {
        ProductVO vo = productCacheService.getWithCache(
                ProductCacheService.detailKey(productId),
                new TypeReference<ProductVO>() {},
                Duration.ofMinutes(30),
                () -> {
                    ProductEntity entity = productMapper.selectById(productId);
                    if (entity == null || entity.getStatus() != 1) {
                        // 不存在/已下架 → 返回 null，由缓存服务写入空值标记防穿透
                        return null;
                    }
                    return toProductVO(entity);
                });
        if (vo == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在或已下架");
        }
        return vo;
    }

    @Override
    public List<SkuInfoDTO> getSkuListByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductSkuEntity> skus = productSkuMapper.selectBatchIds(skuIds);
        if (skus.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有商品 ID，批量查询
        Set<Long> productIds = skus.stream()
                .map(ProductSkuEntity::getProductId)
                .collect(Collectors.toSet());
        Map<Long, ProductEntity> productMap = productMapper.selectBatchIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));

        return skus.stream().map(sku -> {
            SkuInfoDTO dto = new SkuInfoDTO();
            dto.setSkuId(sku.getId());
            dto.setProductId(sku.getProductId());
            dto.setSkuSpecs(sku.getSpecs());
            dto.setPrice(sku.getPrice());
            dto.setStock(sku.getStock());

            // 回填商品信息
            ProductEntity product = productMap.get(sku.getProductId());
            if (product != null) {
                dto.setProductName(product.getName());
                dto.setProductImage(sku.getImage() != null ? sku.getImage() : product.getMainImage());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public SkuInfoDTO getSkuById(Long skuId) {
        ProductSkuEntity sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU不存在");
        }
        ProductEntity product = productMapper.selectById(sku.getProductId());

        SkuInfoDTO dto = new SkuInfoDTO();
        dto.setSkuId(sku.getId());
        dto.setProductId(sku.getProductId());
        dto.setSkuSpecs(sku.getSpecs());
        dto.setPrice(sku.getPrice());
        dto.setStock(sku.getStock());
        if (product != null) {
            dto.setProductName(product.getName());
            dto.setProductImage(sku.getImage() != null ? sku.getImage() : product.getMainImage());
        }
        return dto;
    }

    // ==================== 私有方法 ====================

    /**
     * 执行商品分页查询（用户端，已强制 status=1）。
     */
    private PageResult<ProductVO> queryProductPage(ProductPageQuery query) {
        Page<ProductEntity> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<ProductEntity> wrapper = buildQueryWrapper(query);

        Page<ProductEntity> result = productMapper.selectPage(page, wrapper);

        // 批量预加载品牌/分类名称，避免逐条查询造成的 N+1
        List<ProductEntity> records = result.getRecords();
        Map<Long, String> brandNames = loadBrandNames(records.stream()
                .map(ProductEntity::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> categoryNames = loadCategoryNames(records.stream()
                .map(ProductEntity::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<ProductVO> list = records.stream()
                .map(entity -> toProductVO(entity, brandNames, categoryNames))
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    /**
     * 判断是否为"默认列表"查询：首页第一页、默认每页 20、无任何筛选与排序。
     * 只有该场景可复用统一列表缓存 key，其余参数化查询直接走 DB。
     */
    private boolean isDefaultListQuery(ProductPageQuery q) {
        if (q.getPage() == null || q.getPage() != 1) {
            return false;
        }
        if (q.getSize() != null && q.getSize() != 20) {
            return false;
        }
        if (StringUtils.hasText(q.getKeyword())) {
            return false;
        }
        if (q.getCategoryId() != null || q.getBrandId() != null) {
            return false;
        }
        if (q.getIsHot() != null || q.getIsNew() != null || q.getIsRecommend() != null) {
            return false;
        }
        return !StringUtils.hasText(q.getSortBy());
    }

    /**
     * 构建查询条件。
     */
    private LambdaQueryWrapper<ProductEntity> buildQueryWrapper(ProductPageQuery query) {
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();

        if (query.getCategoryId() != null) {
            wrapper.eq(ProductEntity::getCategoryId, query.getCategoryId());
        }
        if (query.getBrandId() != null) {
            wrapper.eq(ProductEntity::getBrandId, query.getBrandId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ProductEntity::getStatus, query.getStatus());
        }
        if (query.getIsHot() != null) {
            wrapper.eq(ProductEntity::getIsHot, query.getIsHot());
        }
        if (query.getIsNew() != null) {
            wrapper.eq(ProductEntity::getIsNew, query.getIsNew());
        }
        if (query.getIsRecommend() != null) {
            wrapper.eq(ProductEntity::getIsRecommend, query.getIsRecommend());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ProductEntity::getName, query.getKeyword())
                    .or()
                    .like(ProductEntity::getSubtitle, query.getKeyword()));
        }

        // 排序
        String sortBy = query.getSortBy();
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        if (StringUtils.hasText(sortBy)) {
            switch (sortBy) {
                case "price":
                    wrapper.orderBy(true, asc, ProductEntity::getPrice);
                    break;
                case "sales_volume":
                    wrapper.orderBy(true, asc, ProductEntity::getSalesVolume);
                    break;
                case "created_at":
                    wrapper.orderBy(true, asc, ProductEntity::getCreatedAt);
                    break;
                default:
                    wrapper.orderByDesc(ProductEntity::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(ProductEntity::getCreatedAt);
        }

        return wrapper;
    }

    /**
     * 查询商品的 SKU 列表。
     */
    private List<ProductSkuDTO> getSkuList(Long productId) {
        List<ProductSkuEntity> skuEntities = productSkuMapper.selectByProductId(productId);
        if (skuEntities == null || skuEntities.isEmpty()) {
            return Collections.emptyList();
        }
        return skuEntities.stream().map(this::toSkuDTO).collect(Collectors.toList());
    }

    private ProductDTO toProductDTO(ProductEntity entity) {
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setSkuList(getSkuList(entity.getId()));
        return dto;
    }

    private ProductVO toProductVO(ProductEntity entity) {
        Collection<Long> brandIds = entity.getBrandId() == null
                ? Collections.emptyList() : List.of(entity.getBrandId());
        Collection<Long> categoryIds = entity.getCategoryId() == null
                ? Collections.emptyList() : List.of(entity.getCategoryId());
        return toProductVO(entity, loadBrandNames(brandIds), loadCategoryNames(categoryIds));
    }

    /**
     * Entity → VO（品牌/分类名称由调用方批量预加载，供列表场景复用）。
     */
    private ProductVO toProductVO(ProductEntity entity,
                                  Map<Long, String> brandNames,
                                  Map<Long, String> categoryNames) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setSkuList(getSkuList(entity.getId()));
        if (entity.getBrandId() != null) {
            vo.setBrandName(brandNames.get(entity.getBrandId()));
        }
        if (entity.getCategoryId() != null) {
            vo.setCategoryName(categoryNames.get(entity.getCategoryId()));
        }
        return vo;
    }

    /**
     * 批量加载品牌名称（id → name）。
     */
    private Map<Long, String> loadBrandNames(Collection<Long> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(BrandEntity::getId, BrandEntity::getName, (a, b) -> a));
    }

    /**
     * 批量加载分类名称（id → name）。
     */
    private Map<Long, String> loadCategoryNames(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName, (a, b) -> a));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProductStock(Long id, Integer stock) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        entity.setStock(stock);
        productMapper.updateById(entity);
        log.info("商品库存更新: id={}, stock={}", id, stock);
        productCacheService.evict(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkuStock(Long skuId, Integer stock) {
        ProductSkuEntity sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU不存在");
        }
        sku.setStock(stock);
        productSkuMapper.updateById(sku);
        log.info("SKU库存更新: skuId={}, stock={}", skuId, stock);
        productCacheService.evict(sku.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long skuId, Integer quantity) {
        // 库存变更边界兜底：数量必须为正。负数会让 stock >= quantity 恒成立，
        // 且 setSql("stock = stock - " + quantity) 反向变成加库存
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "扣减数量必须大于 0");
        }
        ProductSkuEntity sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU不存在");
        }
        if (sku.getStock() < quantity) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "SKU库存不足");
        }
        LambdaUpdateWrapper<ProductSkuEntity> wrapper = new LambdaUpdateWrapper<ProductSkuEntity>()
                .eq(ProductSkuEntity::getId, skuId)
                .ge(ProductSkuEntity::getStock, quantity)
                .setSql("stock = stock - " + quantity);
        int updated = productSkuMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "SKU库存不足");
        }
        log.info("SKU库存扣减: skuId={}, quantity={}", skuId, quantity);
        // 库存已变化，详情页展示的库存需回源重建
        productCacheService.evict(sku.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "恢复数量必须大于 0");
        }
        ProductSkuEntity sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            // 必须显式失败：调用方（订单取消 / 退款补偿）只 catch 异常、不检查返回值。
            // 原来静默 return 会让「库存没回补」这件事彻底消失 —— 调用方以为成功，
            // 实际库存永久流失且没有任何信号。处理方式与 deductStock 保持一致。
            throw new BusinessException(ResultCode.NOT_FOUND, "SKU不存在，无法恢复库存");
        }
        LambdaUpdateWrapper<ProductSkuEntity> wrapper = new LambdaUpdateWrapper<ProductSkuEntity>()
                .eq(ProductSkuEntity::getId, skuId)
                .setSql("stock = stock + " + quantity);
        productSkuMapper.update(null, wrapper);
        log.info("SKU库存恢复: skuId={}, quantity={}", skuId, quantity);
        productCacheService.evict(sku.getProductId());
    }

    @Override
    public Long countProducts() {
        return productMapper.selectCount(null);
    }

    @Override
    public void updateCommentCount(Long productId, int delta) {
        if (productId == null || delta == 0) {
            return;
        }
        // 用 SQL 自增减，避免读改写竞态；下限 0
        productMapper.update(null, new LambdaUpdateWrapper<ProductEntity>()
                .eq(ProductEntity::getId, productId)
                .setSql("comment_count = GREATEST(comment_count + " + delta + ", 0)"));
        log.debug("回写商品评论数: productId={}, delta={}", productId, delta);
        productCacheService.evict(productId);
    }

    @Override
    public void updateSalesVolume(Long productId, int delta) {
        if (productId == null || delta == 0) {
            return;
        }
        // 用 SQL 自增减，避免读改写竞态；下限 0
        productMapper.update(null, new LambdaUpdateWrapper<ProductEntity>()
                .eq(ProductEntity::getId, productId)
                .setSql("sales_volume = GREATEST(sales_volume + " + delta + ", 0)"));
        log.debug("回写商品销量: productId={}, delta={}", productId, delta);
        // 销量回写后失效缓存，保证热门排序/详情销量展示实时
        productCacheService.evict(productId);
    }

    private ProductSkuDTO toSkuDTO(ProductSkuEntity entity) {
        ProductSkuDTO dto = new ProductSkuDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
