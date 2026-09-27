package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.AddressInfoDTO;
import com.shumamall.agent.dto.OrderInfoDTO;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.DownstreamRequestSigner;
import com.shumamall.agent.feign.OrderFeignClient;
import com.shumamall.agent.feign.UserFeignClient;
import com.shumamall.common.result.PageResult;
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
 * 订单工具：下单 / 订单查询 / 取消（调用 shumamall-order 用户端接口）。
 * <p>
 * 下单为复合操作：先查商品名（product 服务），addressId 缺失时自动取默认地址（user 服务），
 * 最后调用 order 服务创建订单 —— 体现"工具内部跨服务编排"。
 */
@Slf4j
@Component
public class OrderTool extends BaseTool {

    private final OrderFeignClient orderFeignClient;
    private final ProductResolver productResolver;
    private final UserFeignClient userFeignClient;
    private final ObjectMapper objectMapper;
    private final DownstreamRequestSigner requestSigner;

    public OrderTool(OrderFeignClient orderFeignClient,
                     ProductResolver productResolver,
                     UserFeignClient userFeignClient,
                     ObjectMapper objectMapper,
                     DownstreamRequestSigner requestSigner) {
        this.orderFeignClient = orderFeignClient;
        this.productResolver = productResolver;
        this.userFeignClient = userFeignClient;
        this.objectMapper = objectMapper;
        this.requestSigner = requestSigner;
    }

    /**
     * 直接购买下单。
     */
    @Tool(description = "创建订单（直接购买）。推荐传 productKeyword（商品名称关键词），工具自动解析商品ID与默认SKU；"
            + "productId / skuId / addressId 可空，空则自动解析。禁止填 0 或无意义的 ID 占位。")
    public String createOrder(@ToolParam(description = "商品名称关键词（推荐，如 TestPhone / 小米14），工具自动解析商品和默认SKU") String productKeyword,
                              @ToolParam(description = "商品ID（可空，传了则忽略 productKeyword）") Long productId,
                              @ToolParam(description = "SKU ID（可空，自动取商品默认SKU）") Long skuId,
                              @ToolParam(description = "数量，默认1") Integer quantity,
                              @ToolParam(description = "收货地址ID（可空，自动取默认地址）") Long addressId,
                              ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            Long effectiveProductId = productId;
            String productName = null;
            if ((effectiveProductId == null || effectiveProductId == 0)
                    && productKeyword != null && !productKeyword.isBlank()) {
                ProductItemDTO item = productResolver.resolveByKeyword(productKeyword);
                effectiveProductId = item.getId();
                productName = item.getName();
            }
            if (effectiveProductId == null || effectiveProductId == 0) {
                return error("缺少商品信息：请提供商品名称或商品ID");
            }
            Long effectiveSkuId = skuId;
            if (effectiveSkuId == null || effectiveSkuId == 0) {
                effectiveSkuId = productResolver.resolveDefaultSku(effectiveProductId);
            }
            if (productName == null) {
                productName = productResolver.resolveProductName(effectiveProductId);
            }
            Long effectiveAddressId = (addressId == null || addressId == 0)
                    ? resolveDefaultAddress() : addressId;

            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", effectiveProductId);
            payload.put("productName", productName);
            payload.put("skuId", effectiveSkuId);
            payload.put("quantity", quantity == null ? 1 : quantity);
            payload.put("addressId", effectiveAddressId);
            // order 服务的下单接口被 RequestSignFilter 保护，签名必须与最终发送的字节完全一致。
            // 这里用与 Feign 编码同一个 ObjectMapper 序列化同一个 payload 作为待签名原文，
            // 因此二者逐字节相同（HashMap 对同一组键值输出顺序稳定）。
            String requestBody = objectMapper.writeValueAsString(payload);
            DownstreamRequestSigner.SignedRequest signed =
                    requestSigner.sign("POST", "/api/v1/orders", requestBody);
            R<OrderInfoDTO> resp = orderFeignClient.createOrder(
                    payload, signed.timestamp(), signed.nonce(), signed.sign());
            if (!resp.isSuccess() || resp.getData() == null) {
                return error(resp.getMsg());
            }
            log.info("下单成功: userId={}, orderNo={}", userId, resp.getData().getOrderNo());
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("下单失败: userId={}, productId={}", userId, productId, e);
            return error(e);
        }
    }

    /**
     * 查询订单详情。
     */
    @Tool(description = "按订单ID查询订单详情")
    public String getOrderDetail(@ToolParam(description = "订单ID") Long orderId,
                                 ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<OrderInfoDTO> resp = orderFeignClient.orderDetail(orderId);
            if (!resp.isSuccess() || resp.getData() == null) {
                return "{\"empty\":true}";
            }
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("查询订单详情失败: userId={}, orderId={}", userId, orderId, e);
            return error(e);
        }
    }

    /**
     * 查询当前用户订单列表。
     */
    @Tool(description = "查询当前用户的订单列表（最近5条）")
    public String listOrders(ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<PageResult<OrderInfoDTO>> resp = orderFeignClient.orderList(1, 5);
            if (!resp.isSuccess() || resp.getData() == null || resp.getData().getRecords() == null) {
                return "{\"empty\":true}";
            }
            return objectMapper.writeValueAsString(resp.getData().getRecords());
        } catch (Exception e) {
            log.error("查询订单列表失败: userId={}", userId, e);
            return error(e);
        }
    }

    /**
     * 取消订单（高风险操作，编排器要求 LLM 先向用户二次确认）。
     */
    @Tool(description = "取消订单，参数 orderId。注意：这是高风险操作，调用前必须先征得用户明确确认")
    public String cancelOrder(@ToolParam(description = "订单ID") Long orderId,
                              ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<Void> resp = orderFeignClient.cancelOrder(orderId);
            if (!resp.isSuccess()) {
                return error(resp.getMsg());
            }
            log.info("取消订单成功: userId={}, orderId={}", userId, orderId);
            return "{\"success\":true,\"message\":\"订单已取消\"}";
        } catch (Exception e) {
            log.error("取消订单失败: userId={}, orderId={}", userId, orderId, e);
            return error(e);
        }
    }

    // ==================== 内部辅助 ====================

    private Long resolveDefaultAddress() {
        R<List<AddressInfoDTO>> resp = userFeignClient.addressList();
        if (!resp.isSuccess() || resp.getData() == null || resp.getData().isEmpty()) {
            throw new IllegalStateException("未找到收货地址，请先添加收货地址");
        }
        return resp.getData().stream()
                .filter(a -> a.getIsDefault() != null && a.getIsDefault() == 1)
                .findFirst()
                .orElse(resp.getData().get(0))
                .getId();
    }
}
