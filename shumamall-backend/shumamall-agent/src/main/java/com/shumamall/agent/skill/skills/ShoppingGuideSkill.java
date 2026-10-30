package com.shumamall.agent.skill.skills;

import com.shumamall.agent.config.AgentPromptProvider;
import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 导购技能：商品搜索 / 推荐 / 对比。
 */
@Component
public class ShoppingGuideSkill implements AgentSkill {

    private final AgentPromptProvider promptProvider;

    public ShoppingGuideSkill(AgentPromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

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
        return promptProvider.systemPromptForSkill(getSkillId());
    }

    @Override
    public boolean usesNativeToolCalling() {
        return true;
    }
}
