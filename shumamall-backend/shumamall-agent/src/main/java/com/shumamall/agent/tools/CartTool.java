package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.CartItemDTO;
import com.shumamall.agent.feign.OrderFeignClient;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车工具：查询 / 加购（调用 shumamall-order 用户端接口，身份由 JWT 透传）。
 */
@Slf4j
@Component
public class CartTool extends BaseTool {

    private final OrderFeignClient orderFeignClient;
    private final ProductResolver productResolver;
    private final ObjectMapper objectMapper;

    public CartTool(OrderFeignClient orderFeignClient, ProductResolver productResolver, ObjectMapper objectMapper) {
        this.orderFeignClient = orderFeignClient;
        this.productResolver = productResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 查看当前用户购物车。
     */
    @Tool(description = "查看当前用户的购物车列表")
    public String getCart(ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<List<CartItemDTO>> resp = orderFeignClient.cartList();
            if (!resp.isSuccess() || resp.getData() == null) {
                return "{\"empty\":true}";
            }
            log.info("查询购物车: userId={}, items={}", userId, resp.getData().size());
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("查询购物车失败: userId={}", userId, e);
            return error(e);
        }
    }

    /**
     * 添加商品到购物车。
     */
    @Tool(description = "添加商品到购物车。推荐传 productKeyword（商品名称关键词），工具自动解析商品ID与默认SKU；"
            + "productId / skuId 可空，空则自动解析。禁止填 0 或无意义的 ID 占位。")
    public String addToCart(@ToolParam(description = "商品名称关键词（推荐，如 TestPhone / 小米14），工具自动解析商品和默认SKU") String productKeyword,
                            @ToolParam(description = "商品ID（可空，传了则忽略 productKeyword）") Long productId,
                            @ToolParam(description = "SKU ID（可空，自动取商品默认SKU）") Long skuId,
                            @ToolParam(description = "数量，默认1") Integer quantity,
                            ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            Long effectiveProductId = productId;
            if ((effectiveProductId == null || effectiveProductId == 0)
                    && productKeyword != null && !productKeyword.isBlank()) {
                effectiveProductId = productResolver.resolveByKeyword(productKeyword).getId();
            }
            if (effectiveProductId == null || effectiveProductId == 0) {
                return error("缺少商品信息：请提供商品名称或商品ID");
            }
            Long effectiveSkuId = skuId;
            if (effectiveSkuId == null || effectiveSkuId == 0) {
                effectiveSkuId = productResolver.resolveDefaultSku(effectiveProductId);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", effectiveProductId);
            payload.put("skuId", effectiveSkuId);
            payload.put("quantity", quantity == null ? 1 : quantity);
            R<Void> resp = orderFeignClient.addCart(payload);
            if (!resp.isSuccess()) {
                return error(resp.getMsg());
            }
            log.info("加购成功: userId={}, productId={}, skuId={}", userId, effectiveProductId, effectiveSkuId);
            return "{\"success\":true,\"message\":\"已加入购物车\"}";
        } catch (Exception e) {
            log.error("加购失败: userId={}, productId={}", userId, productId, e);
            return error(e);
        }
    }
}
