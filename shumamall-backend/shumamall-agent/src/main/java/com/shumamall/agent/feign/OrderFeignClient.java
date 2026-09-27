package com.shumamall.agent.feign;

import com.shumamall.agent.dto.CartItemDTO;
import com.shumamall.agent.dto.OrderInfoDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单服务 Feign 客户端（shumamall-order 用户端接口，需携带 JWT 透传 userId）。
 */
@FeignClient(name = "shumamall-order")
public interface OrderFeignClient {

    /**
     * 创建订单。
     * <p>
     * 该接口在 order 服务侧被 {@code RequestSignFilter} 保护，必须携带 HMAC 签名头，
     * 否则会被拒 {@code 4013 缺少请求签名头}。签名由
     * {@link DownstreamRequestSigner} 基于透传的 JWT 现场推导密钥生成。
     *
     * @param payload 创建订单参数（productId / productName / skuId / quantity / addressId / remark）
     * @param timestamp 签名头 X-Timestamp（epoch 毫秒）
     * @param nonce     签名头 X-Nonce（一次性随机串）
     * @param sign      签名头 X-Sign（Base64(HMAC-SHA256)）
     * @return 订单详情
     */
    @PostMapping("/api/v1/orders")
    R<OrderInfoDTO> createOrder(@RequestBody Map<String, Object> payload,
                                @RequestHeader("X-Timestamp") String timestamp,
                                @RequestHeader("X-Nonce") String nonce,
                                @RequestHeader("X-Sign") String sign);

    /**
     * 查询订单详情。
     */
    @GetMapping("/api/v1/orders/{id}")
    R<OrderInfoDTO> orderDetail(@PathVariable("id") Long id);

    /**
     * 分页查询当前用户订单列表。
     */
    @GetMapping("/api/v1/orders")
    R<PageResult<OrderInfoDTO>> orderList(@RequestParam("page") Integer page,
                                          @RequestParam("size") Integer size);

    /**
     * 取消订单。
     */
    @PutMapping("/api/v1/orders/{id}/cancel")
    R<Void> cancelOrder(@PathVariable("id") Long id);

    /**
     * 获取当前用户购物车列表。
     */
    @GetMapping("/api/v1/cart")
    R<List<CartItemDTO>> cartList();

    /**
     * 添加商品到购物车。
     *
     * @param payload 添加参数（skuId / productId / quantity）
     */
    @PostMapping("/api/v1/cart")
    R<Void> addCart(@RequestBody Map<String, Object> payload);
}
