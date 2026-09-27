package com.shumamall.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单状态枚举。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /** 待付款 */
    PENDING_PAYMENT(0, "待付款"),

    /** 待发货 */
    PENDING_DELIVERY(1, "待发货"),

    /** 待收货 */
    PENDING_RECEIVE(2, "待收货"),

    /** 已完成 */
    COMPLETED(3, "已完成"),

    /** 已取消 */
    CANCELLED(4, "已取消"),

    /** 售后中 */
    AFTER_SALE(5, "售后中");

    private final int code;
    private final String label;

    private static final Map<Integer, OrderStatusEnum> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(OrderStatusEnum::getCode, e -> e));

    /**
     * 允许的迁移链路：只放行「发货（待发货 → 待收货）」与「确认收货（待收货 → 已完成）」
     * 两条由管理端推进的链路，其余一律拒绝，原因是它们各自会让订单绕过应有的业务动作：
     * <ul>
     *   <li>待付款 → 待发货：订单尚未收款却被置为已支付，而营收统计口径是 {@code status IN (1,2,3,5)}，
     *       未收款订单会被算进销售额；</li>
     *   <li>任意 → 已取消：直接改状态不会回补库存与回退销量，取消必须走用户取消（回补库存）
     *       或管理员退款（回补库存并回退销量）接口；</li>
     *   <li>任意 → 售后中：当前没有售后流程实现，该状态不可达。</li>
     * </ul>
     */
    private static final Map<OrderStatusEnum, Set<OrderStatusEnum>> ALLOWED_TRANSITIONS = Map.of(
            PENDING_DELIVERY, EnumSet.of(PENDING_RECEIVE),
            PENDING_RECEIVE, EnumSet.of(COMPLETED));

    /**
     * 根据编码获取枚举。
     *
     * @param code 状态编码
     * @return 枚举实例，未找到返回 null
     */
    public static OrderStatusEnum of(Integer code) {
        return code == null ? null : MAP.get(code);
    }

    /**
     * 判断当前状态是否允许迁移到目标状态。
     *
     * @param target 目标状态
     * @return 是否允许迁移；目标状态为空时返回 false
     */
    public boolean canTransitionTo(OrderStatusEnum target) {
        if (target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 根据编码获取标签。
     *
     * @param code 状态编码
     * @return 状态标签
     */
    public static String getLabel(Integer code) {
        OrderStatusEnum e = of(code);
        return e == null ? null : e.getLabel();
    }
}
