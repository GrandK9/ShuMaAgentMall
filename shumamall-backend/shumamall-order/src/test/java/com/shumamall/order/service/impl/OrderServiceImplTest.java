package com.shumamall.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.order.api.ProductFeignClient;
import com.shumamall.order.api.UserFeignClient;
import com.shumamall.order.dao.CartMapper;
import com.shumamall.order.dao.OrderItemMapper;
import com.shumamall.order.dao.OrderMapper;
import com.shumamall.order.dto.OrderCreateDTO;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.entity.OrderEntity;
import com.shumamall.order.entity.OrderItemEntity;
import com.shumamall.order.enums.OrderStatusEnum;
import com.shumamall.order.timeout.mq.OrderTimeoutMessageProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单服务核心路径单元测试（仅编译验证，不依赖 Spring 上下文）。
 * <p>
 * 覆盖：直接购买下单、订单取消状态机、支付回调状态校验。
 */
class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
    private final ProductFeignClient productFeignClient = mock(ProductFeignClient.class);
    private final UserFeignClient userFeignClient = mock(UserFeignClient.class);
    private final CartMapper cartMapper = mock(CartMapper.class);
    private final OrderTimeoutMessageProducer orderTimeoutMessageProducer = mock(OrderTimeoutMessageProducer.class);

    private final OrderServiceImpl service =
            new OrderServiceImpl(orderMapper, orderItemMapper, productFeignClient,
                    userFeignClient, cartMapper, new ObjectMapper(), orderTimeoutMessageProducer);

    private OrderEntity order(Long id, Long userId, int status) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }

    /**
     * 初始化 MyBatis-Plus 的实体元数据缓存。
     * <p>
     * {@code LambdaUpdateWrapper.eq(OrderEntity::getStatus, ...)} 需要在元数据缓存里查得到
     * OrderEntity 的字段映射，正常由 Spring 启动扫描 Mapper 时建立。本测试不启动 Spring 上下文，
     * 缺这一步会在构造条件更新时抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderEntity.class);
    }

    private SkuInfoDTO skuInfo() {
        SkuInfoDTO dto = new SkuInfoDTO();
        dto.setSkuId(10L);
        dto.setProductId(100L);
        dto.setProductName("测试手机");
        dto.setSkuSpecs("黑色 256G");
        dto.setProductImage("http://img/x.jpg");
        dto.setPrice(new BigDecimal("1999.00"));
        return dto;
    }

    // ==================== 直接购买下单 ====================

    @Test
    void createOrder_directBuy_success() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setSkuId(10L);
        dto.setQuantity(2);
        dto.setProductName("测试手机");
        dto.setAddressId(1L);

        when(productFeignClient.getSkuById(10L)).thenReturn(R.ok(skuInfo()));
        when(orderMapper.selectById(any())).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderItemMapper.selectByOrderId(any())).thenReturn(Collections.emptyList());

        OrderDTO result = service.createOrder(1L, dto);

        assertNotNull(result);
        verify(orderMapper).insert(any(OrderEntity.class));
        verify(orderItemMapper).insert(any(OrderItemEntity.class));
        verify(productFeignClient).deductStock(10L, 2);
    }

    // ==================== 取消订单状态机 ====================

    @Test
    void cancelOrder_throws_whenNotPendingPayment() {
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 1L, OrderStatusEnum.COMPLETED.getCode()));

        assertThrows(BusinessException.class, () -> service.cancelOrder(1L, 1L));
    }

    @Test
    void cancelOrder_throws_whenNotOwner() {
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));

        assertThrows(BusinessException.class, () -> service.cancelOrder(1L, 2L));
    }

    @Test
    void cancelOrder_success() {
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderItemMapper.selectByOrderId(any())).thenReturn(Collections.emptyList());
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelOrder(1L, 1L);

        // 状态更新改为条件更新（CAS），影响行数 1 表示抢到了状态迁移
        verify(orderMapper).update(any(), any());
    }

    @Test
    void cancelOrder_throws_whenConcurrentCancelWon() {
        // 并发场景：另一个请求（或超时任务）已把状态改走，CAS 影响行数为 0
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderMapper.update(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(1L, 1L));

        assertEquals(ResultCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
        // 抢不到就不该回补库存，否则库存被重复加回
        verify(productFeignClient, never()).restoreStock(any(), any());
    }

    // ==================== 支付回调状态机 ====================

    @Test
    void updateOrderPaid_throws_whenStatusNotPending() {
        when(orderMapper.selectOne(any())).thenReturn(order(1L, 1L, OrderStatusEnum.COMPLETED.getCode()));

        assertThrows(BusinessException.class, () -> service.updateOrderPaid("ORD001", 1, "PAY001"));
    }

    @Test
    void updateOrderPaid_success() {
        when(orderMapper.selectOne(any())).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderItemMapper.selectByOrderId(any())).thenReturn(Collections.emptyList());
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.updateOrderPaid("ORD001", 1, "PAY001");

        // 条件更新（CAS）：影响行数 1 表示抢到了「待付款 → 待发货」迁移
        verify(orderMapper).update(any(), any());
    }

    // ==================== 退款补偿链 ====================

    private OrderItemEntity item(Long skuId, Long productId, int quantity) {
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(1L);
        item.setSkuId(skuId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void updateOrderRefunded_throws_whenStatusNotPendingDelivery() {
        when(orderMapper.selectOne(any())).thenReturn(order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode()));

        assertThrows(BusinessException.class, () -> service.updateOrderRefunded("ORD001"));
    }

    @Test
    void updateOrderRefunded_回补库存并负向回退销量() {
        OrderEntity order = order(1L, 1L, OrderStatusEnum.PENDING_DELIVERY.getCode());
        order.setOrderNo("ORD001");
        when(orderMapper.selectOne(any())).thenReturn(order);
        // 同商品两个 SKU（各 2 件）→ 销量按商品聚合回退 -4，库存按 SKU 各补 2
        when(orderItemMapper.selectByOrderId(any())).thenReturn(List.of(
                item(10L, 100L, 2),
                item(11L, 100L, 2)
        ));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.updateOrderRefunded("ORD001");

        verify(orderMapper).update(any(), any());
        verify(productFeignClient).restoreStock(10L, 2);
        verify(productFeignClient).restoreStock(11L, 2);
        verify(productFeignClient).updateSalesVolume(100L, -4);
    }

    @Test
    void updateOrderRefunded_库存回补失败仍完成退款() {
        OrderEntity order = order(1L, 1L, OrderStatusEnum.PENDING_DELIVERY.getCode());
        order.setOrderNo("ORD001");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderItemMapper.selectByOrderId(any())).thenReturn(List.of(item(10L, 100L, 1)));
        when(orderMapper.update(any(), any())).thenReturn(1);
        doThrow(new RuntimeException("product service down"))
                .when(productFeignClient).restoreStock(any(), any());

        service.updateOrderRefunded("ORD001");

        verify(orderMapper).update(any(), any());
    }

    // ==================== 应付金额查询（支付前强校验） ====================

    @Test
    void getPayableAmount_returnsPayAmount() {
        OrderEntity order = order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setPayAmount(new BigDecimal("1999.00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertEquals(new BigDecimal("1999.00"), service.getPayableAmount("ORD001", 1L));
    }

    @Test
    void getPayableAmount_throws_whenNotOwner() {
        // 越权代付：A 用户拿 B 用户的订单号来查应付金额，必须被拒
        OrderEntity order = order(1L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setPayAmount(new BigDecimal("1999.00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getPayableAmount("ORD001", 2L));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void getPayableAmount_throws_whenOrderNotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getPayableAmount("ORD404", 1L));

        assertEquals(ResultCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }
}
