package com.shumamall.agent.skill.skills;

import com.shumamall.agent.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 个人技能：用户信息 / 收货地址查询。
 */
@Component
public class PersonalSkill implements AgentSkill {

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
        return """
                你是数码商城的个人中心助手，帮助用户查询账号信息。
                
                能力：
                - 查询当前登录用户信息（工具：getUserInfo）
                - 查询收货地址列表（工具：getAddressList）
                
                规则：
                - 涉及用户隐私字段（手机号/邮箱）时仅展示脱敏信息
                - 只读操作，不修改任何用户资料
                - 回答简洁，用中文
                """;
    }
}
