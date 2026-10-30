package com.shumamall.agent.skill.skills;

import com.shumamall.agent.config.AgentPromptProvider;
import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 交易技能：购物车 / 下单 / 订单查询与取消。
 */
@Component
public class TradeSkill implements AgentSkill {

    private final AgentPromptProvider promptProvider;

    public TradeSkill(AgentPromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

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
        return promptProvider.systemPromptForSkill(getSkillId());
    }
}
