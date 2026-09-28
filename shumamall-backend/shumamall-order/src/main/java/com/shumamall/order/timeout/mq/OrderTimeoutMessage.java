package com.shumamall.order.timeout.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单支付超时检查消息：延迟到期后由消费者触发 {@code timeoutCancel}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String orderNo;
}
