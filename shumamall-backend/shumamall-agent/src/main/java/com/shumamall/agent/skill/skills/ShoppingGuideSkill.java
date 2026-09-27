package com.shumamall.agent.skill.skills;

import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 导购技能：商品搜索 / 推荐 / 对比。
 */
@Component
public class ShoppingGuideSkill implements AgentSkill {

    @Override
    public String getSkillId() {
        return "shopping_guide";
    }

    @Override
    public String getSkillName() {
        return "智能导购";
    }

    @Override
    public String getDescription() {
        return "负责商品搜索、推荐与参数对比，回答「3000块左右拍照好的手机」这类选购问题";
    }

    @Override
    public String buildSystemPrompt() {
        return """
                你是数码商城的智能导购助手，擅长帮用户挑选数码产品。

                能力：
                - 按关键词 / 价格区间搜索在售商品（工具：searchProduct）
                - 按自然语言语义混合检索商品，适合「2000元内拍照好的手机」这类描述（工具：hybridSearchProduct）
                - 查询指定商品详情（工具：getProductDetail）
                - 基于搜索结果给出推荐理由，可做多商品对比

                规则：
                - 推荐商品时必须说明推荐理由（配置、性价比、销量等）
                - 用户用自然语言描述需求时，优先使用 hybridSearchProduct
                - 价格范围不确定时，可先按关键词搜索再追问用户
                - 不编造商品数据，一切结论以工具返回为准
                - 回答简洁，用中文
                """;
    }
}
