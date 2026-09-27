package com.shumamall.product.service;

import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.product.dto.ProductDTO;
import com.shumamall.product.dto.ProductPageQuery;
import com.shumamall.product.dto.ProductVO;

import java.util.List;

/**
 * 商品服务接口。
 */
public interface ProductService {

    /**
     * 分页查询商品（管理端）。
     *
     * @param query 查询参数
     * @return 商品分页结果
     */
    PageResult<ProductDTO> pageQuery(ProductPageQuery query);

    /**
     * 根据ID查询商品详情（含SKU列表）。
     *
     * @param id 商品ID
     * @return 商品详情
     */
    ProductDTO getById(Long id);

    /**
     * 创建商品（含SKU）。
     *
     * @param productDTO 商品信息
     * @return 商品ID
     */
    Long create(ProductDTO productDTO);

    /**
     * 更新商品（含SKU）。
     *
     * @param productDTO 商品信息
     */
    void update(ProductDTO productDTO);

    /**
     * 更新商品上下架状态。
     *
     * @param id     商品ID
     * @param status 状态 0-下架 1-上架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除商品（含关联SKU）。
     *
     * @param id 商品ID
     */
    void delete(Long id);

    /**
     * 用户端分页查询商品列表（仅已上架）。
     *
     * @param query 查询参数
     * @return 商品分页结果
     */
    PageResult<ProductVO> getUserFacingList(ProductPageQuery query);

    /**
     * 用户端查询商品详情（仅已上架）。
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    ProductVO getUserFacingDetail(Long productId);

    /**
     * 更新商品默认库存（管理端直接设置商品表 stock 字段）。
     *
     * @param id    商品ID
     * @param stock 新库存量
     */
    void updateProductStock(Long id, Integer stock);

    /**
     * 批量查询 SKU 信息。
     *
     * @param skuIds SKU ID 列表
     * @return SKU 信息列表
     */
    List<SkuInfoDTO> getSkuListByIds(List<Long> skuIds);

    /**
     * 根据 SKU ID 查询 SKU 信息。
     *
     * @param skuId SKU ID
     * @return SKU 信息
     */
    SkuInfoDTO getSkuById(Long skuId);

    /**
     * 更新SKU库存（管理端直接设置）。
     *
     * @param skuId  SKU ID
     * @param stock  新库存量
     */
    void updateSkuStock(Long skuId, Integer stock);

    /**
     * 扣减SKU库存，库存不足时抛出异常。
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     */
    void deductStock(Long skuId, Integer quantity);

    /**
     * 恢复SKU库存。
     *
     * @param skuId    SKU ID
     * @param quantity 恢复数量
     */
    void restoreStock(Long skuId, Integer quantity);

    /**
     * 商品总数（供管理端仪表盘聚合调用）。
     *
     * @return 商品总数
     */
    Long countProducts();

    /**
     * 调整商品评论数（评论区服务发评/删除时回写）。
     *
     * @param productId 商品ID
     * @param delta     增量（发评 +1，删除 -1）
     */
    void updateCommentCount(Long productId, int delta);

    /**
     * 调整商品销量（订单服务支付成功/取消退款时回写，支撑热门商品排序）。
     *
     * @param productId 商品ID
     * @param delta     增量（支付成功 +1，取消退款 -1）
     */
    void updateSalesVolume(Long productId, int delta);
}
