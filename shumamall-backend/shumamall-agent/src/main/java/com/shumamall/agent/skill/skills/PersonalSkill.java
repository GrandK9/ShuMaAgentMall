package com.shumamall.agent.skill.skills;

import com.shumamall.agent.config.AgentPromptProvider;
import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 个人技能：用户信息 / 收货地址查询。
 */
@Component
public class PersonalSkill implements AgentSkill {

    private final AgentPromptProvider promptProvider;

    public PersonalSkill(AgentPromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

    @Override
    public String getSkillId() {
        return "personal";
    }

    @Override
    public String getSkillName() {
        return "个人中心";
    }

    @Override
    public String getDescription() {
        return "负责查询当前用户个人信息、收货地址等账号相关内容";
    }

    @Override
    public String buildSystemPrompt() {
        return promptProvider.systemPromptForSkill(getSkillId());
    }
}
