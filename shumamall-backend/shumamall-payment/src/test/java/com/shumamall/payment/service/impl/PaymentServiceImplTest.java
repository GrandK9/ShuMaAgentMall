package com.shumamall.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.payment.api.OrderFeignClient;
import com.shumamall.payment.dao.PaymentRecordMapper;
import com.shumamall.payment.dto.PayRequestDTO;
import com.shumamall.payment.dto.PayResponseDTO;
import com.shumamall.payment.entity.PaymentRecordEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付服务核心路径单元测试（仅编译验证，不依赖 Spring 上下文）。
 * <p>
 * 覆盖：支付成功主流程、订单服务不可用兜底、退款状态机校验。
 */
class PaymentServiceImplTest {

    private final PaymentRecordMapper paymentRecordMapper = mock(PaymentRecordMapper.class);
    private final OrderFeignClient orderFeignClient = mock(OrderFeignClient.class);

    private final PaymentServiceImpl service =
            new PaymentServiceImpl(paymentRecordMapper, orderFeignClient, new ObjectMapper());

    private PayRequestDTO payRequest() {
        PayRequestDTO dto = new PayRequestDTO();
        dto.setOrderNo("ORD001");
        dto.setPaymentMethod(1);
        dto.setAmount(new BigDecimal("1999.00"));
        return dto;
    }

    private PaymentRecordEntity paidRecord() {
        PaymentRecordEntity entity = new PaymentRecordEntity();
        entity.setId(1L);
        entity.setPaymentNo("PAY001");
        entity.setOrderNo("ORD001");
        entity.setStatus(1);
        return entity;
    }

    // ==================== 支付 ====================

    /** 订单服务返回的应付金额与请求体一致 */
    private void stubPayableAmount(String orderNo, String amount) {
        when(orderFeignClient.getPayableAmount(orderNo, 1L))
                .thenReturn(R.ok(new BigDecimal(amount)));
    }

    @Test
    void pay_success() {
        stubPayableAmount("ORD001", "1999.00");

        PayResponseDTO response = service.pay(1L, payRequest());

        assertEquals(1, response.getStatus());
        assertEquals("ORD001", response.getOrderNo());
        assertEquals(new BigDecimal("1999.00"), response.getAmount());
        verify(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        verify(orderFeignClient).updateOrderPaid("ORD001", 1, response.getPaymentNo());
    }

    @Test
    void pay_throws_whenAmountMismatch() {
        // 客户端篡改金额（1999 元订单只付 0.01 元）必须被拒，且不落支付记录、不通知订单服务
        stubPayableAmount("ORD001", "1999.00");
        PayRequestDTO dto = payRequest();
        dto.setAmount(new BigDecimal("0.01"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pay(1L, dto));

        assertEquals(ResultCode.PAY_AMOUNT_MISMATCH.getCode(), ex.getCode());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(orderFeignClient, never()).updateOrderPaid(any(), any(), any());
    }

    @Test
    void pay_throws_whenOrderNotFound() {
        when(orderFeignClient.getPayableAmount("ORD001", 1L))
                .thenReturn(R.failed(ResultCode.ORDER_NOT_FOUND));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pay(1L, payRequest()));

        assertEquals(ResultCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
    }

    @Test
    void pay_throws_whenOrderServiceUnavailable() {
        stubPayableAmount("ORD001", "1999.00");
        doThrow(new RuntimeException("order service down"))
                .when(orderFeignClient).updateOrderPaid(any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pay(1L, payRequest()));

        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    // ==================== 退款状态机 ====================

    @Test
    void refund_throws_whenRecordNotFound() {
        when(paymentRecordMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.refund(999L, 9L));
    }

    @Test
    void refund_throws_whenNotPaid() {
        PaymentRecordEntity entity = paidRecord();
        entity.setStatus(2); // 支付失败，不允许退款
        when(paymentRecordMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refund(1L, 9L));

        assertEquals(ResultCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void refund_success() {
        when(paymentRecordMapper.selectById(1L)).thenReturn(paidRecord());

        service.refund(1L, 9L);

        verify(orderFeignClient).updateOrderRefunded("ORD001");
    }
}
