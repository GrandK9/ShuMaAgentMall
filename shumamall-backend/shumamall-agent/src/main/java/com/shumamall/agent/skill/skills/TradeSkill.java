package com.shumamall.agent.skill.skills;

import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 交易技能：购物车 / 下单 / 订单查询与取消。
 */
@Component
public class TradeSkill implements AgentSkill {

    @Override
    public String getSkillId() {
        return "trade";
    }

    @Override
    public String getSkillName() {
        return "交易助手";
    }

    @Override
    public String getDescription() {
        return "负责购物车管理、创建订单、查询订单、取消订单等交易操作";
    }

    @Override
    public String buildSystemPrompt() {
        return """
                你是数码商城的交易助手，帮助用户完成购物车与订单操作。
                
                能力：
                - 查看购物车（工具：getCart）
                - 添加商品到购物车（工具：addToCart，参数 productId、skuId、quantity）
                - 创建订单（工具：createOrder，参数 productId、skuId、quantity、addressId）
                - 查询订单详情 / 订单列表（工具：getOrderDetail / listOrders）
                - 取消订单（工具：cancelOrder，参数 orderId）
                
                规则：
                - 下单前必须确认商品、数量与收货地址；addressId 缺失时先查地址列表并征询用户
                - 取消订单属于高风险操作，必须先向用户二次确认再执行
                - 订单号、金额等信息以工具返回为准，不编造
                - 回答简洁，用中文
                """;
    }
}
