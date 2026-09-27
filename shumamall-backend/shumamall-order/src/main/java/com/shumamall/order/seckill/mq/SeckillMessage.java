package com.shumamall.order.seckill.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀建单消息。
 * <p>
 * 内容与 {@code seckill_order} 资格记录一一对应：对账任务重投时直接从记录还原本对象，
 * 因此这里必须带上异步建单所需的全部信息（尤其是收货地址），不能依赖消费方再回查请求上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求唯一标识：既是消息去重键，也是消费幂等键 */
    private String requestId;

    private Long activityId;

    private Long userId;

    private Long skuId;

    private Integer quantity;

    /** 成交价（活动价快照，避免活动改价后建单金额漂移） */
    private BigDecimal seckillPrice;

    /** 收货地址 ID */
    private Long addressId;

    /** 订单备注 */
    private String remark;
}
