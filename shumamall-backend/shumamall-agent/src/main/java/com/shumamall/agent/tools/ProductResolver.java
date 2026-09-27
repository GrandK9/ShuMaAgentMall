package com.shumamall.agent.tools;

import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 商品解析辅助：按关键词定位商品 / 取商品默认 SKU / 解析商品名。
 * <p>
 * 下单、加购工具共用。规划器（Plan-then-Execute）执行前拿不到商品 ID，
 * 因此工具入参以<b>商品名称关键词</b>为主，ID 由工具内部调用 product 服务解析，
 * 避免 LLM 填写 0 / 占位 ID 导致下游 404。
 */
@Slf4j
@Component
public class ProductResolver {

    private final ProductFeignClient productFeignClient;

    public ProductResolver(ProductFeignClient productFeignClient) {
        this.productFeignClient = productFeignClient;
    }

    /**
     * 按关键词搜索在售商品，返回销量最高的第一个。
     *
     * @throws IllegalStateException 无匹配商品
     */
    public ProductItemDTO resolveByKeyword(String keyword) {
        R<PageResult<ProductItemDTO>> resp = productFeignClient.list(
                1, 5, null, null, keyword, 1, null, null, null, "sales_volume", "desc");
        if (resp.isSuccess() && resp.getData() != null
                && resp.getData().getRecords() != null && !resp.getData().getRecords().isEmpty()) {
            return resp.getData().getRecords().get(0);
        }
        throw new IllegalStateException("未找到商品: " + keyword);
    }

    /**
     * 取商品详情中的默认 SKU ID（优先启用状态的第一个）。
     *
     * @throws IllegalStateException 商品无可用 SKU
     */
    public Long resolveDefaultSku(Long productId) {
        R<ProductItemDTO> resp = productFeignClient.detail(productId);
        if (resp.isSuccess() && resp.getData() != null
                && resp.getData().getSkuList() != null && !resp.getData().getSkuList().isEmpty()) {
            return resp.getData().getSkuList().stream()
                    .filter(s -> s.getStatus() == null || s.getStatus() == 1)
                    .findFirst()
                    .orElse(resp.getData().getSkuList().get(0))
                    .getId();
        }
        throw new IllegalStateException("商品无可用SKU: productId=" + productId);
    }

    /**
     * 按商品 ID 解析商品名称。
     *
     * @throws IllegalStateException 商品不存在
     */
    public String resolveProductName(Long productId) {
        R<ProductItemDTO> resp = productFeignClient.detail(productId);
        if (resp.isSuccess() && resp.getData() != null && resp.getData().getName() != null) {
            return resp.getData().getName();
        }
        throw new IllegalStateException("商品不存在: productId=" + productId);
    }
}
