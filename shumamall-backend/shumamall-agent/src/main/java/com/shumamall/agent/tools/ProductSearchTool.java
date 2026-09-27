package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品工具：商品搜索 / 详情查询（调用 shumamall-product 用户端接口）。
 */
@Slf4j
@Component
public class ProductSearchTool extends BaseTool {

    private final ProductFeignClient productFeignClient;
    private final ObjectMapper objectMapper;

    public ProductSearchTool(ProductFeignClient productFeignClient, ObjectMapper objectMapper) {
        this.productFeignClient = productFeignClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 按关键词 / 价格区间搜索在售商品，按销量排序，返回精简商品列表 JSON。
     */
    @Tool(description = "按关键词和可选价格区间搜索在售商品（按销量排序），返回商品列表")
    public String searchProduct(@ToolParam(description = "搜索关键词，如 手机/平板") String keyword,
                                @ToolParam(description = "最低价格（可空）") Double priceMin,
                                @ToolParam(description = "最高价格（可空）") Double priceMax,
                                @ToolParam(description = "返回条数，默认5最多20") Integer limit,
                                ToolContext toolContext) {
        try {
            int size = limit == null || limit <= 0 ? 5 : Math.min(limit, 20);
            R<PageResult<ProductItemDTO>> resp = productFeignClient.list(
                    1, size, null, null, keyword, 1, null, null, null, "sales_volume", "desc");
            if (!resp.isSuccess() || resp.getData() == null || resp.getData().getRecords() == null) {
                return "{\"empty\":true}";
            }
            List<ProductItemDTO> filtered = new ArrayList<>();
            for (ProductItemDTO item : resp.getData().getRecords()) {
                BigDecimal price = item.getPrice();
                if (priceMin != null && price.compareTo(BigDecimal.valueOf(priceMin)) < 0) {
                    continue;
                }
                if (priceMax != null && price.compareTo(BigDecimal.valueOf(priceMax)) > 0) {
                    continue;
                }
                filtered.add(item);
            }
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            log.error("搜索商品失败: keyword={}", keyword, e);
            return error(e);
        }
    }

    /**
     * 查询单个商品详情。
     */
    @Tool(description = "按商品ID查询商品详情")
    public String getProductDetail(@ToolParam(description = "商品ID") Long productId,
                                   ToolContext toolContext) {
        try {
            R<ProductItemDTO> resp = productFeignClient.detail(productId);
            if (!resp.isSuccess() || resp.getData() == null) {
                return "{\"empty\":true}";
            }
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("查询商品详情失败: productId={}", productId, e);
            return error(e);
        }
    }
}
