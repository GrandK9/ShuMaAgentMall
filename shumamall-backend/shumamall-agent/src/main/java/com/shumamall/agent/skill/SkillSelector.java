package com.shumamall.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 技能选择器：根据用户消息路由到合适的技能。
 * <p>
 * 默认走 LLM 路由（结构化输出 {@link SkillSelection}）；
 * mock-llm 模式下退化为关键词规则，保证无真实模型时链路可演示。
 */
@Slf4j
@Component
public class SkillSelector {

    private static final String DEFAULT_SKILL_ID = "shopping_guide";

    private final SkillRegistry skillRegistry;
    private final ChatClient chatClient;
    private final boolean mockLlm;

    public SkillSelector(SkillRegistry skillRegistry,
                         ChatClient chatClient,
                         @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm) {
        this.skillRegistry = skillRegistry;
        this.chatClient = chatClient;
        this.mockLlm = mockLlm;
    }

    /**
     * 为当前消息选择技能。优先尊重用户显式指定的技能。
     *
     * @param userMessage      用户消息
     * @param preferredSkillId 前端指定的技能 ID（可为空）
     * @return 命中的技能，兜底为导购技能
     */
    public AgentSkill select(String userMessage, String preferredSkillId) {
        if (preferredSkillId != null && !preferredSkillId.isBlank()) {
            AgentSkill preferred = skillRegistry.findById(preferredSkillId);
            if (preferred != null) {
                return preferred;
            }
        }
        AgentSkill skill = mockLlm ? ruleBasedSelect(userMessage) : llmSelect(userMessage);
        if (skill == null) {
            skill = skillRegistry.findById(DEFAULT_SKILL_ID);
        }
        log.info("技能路由结果: skillId={}", skill.getSkillId());
        return skill;
    }

    private AgentSkill llmSelect(String userMessage) {
        try {
            BeanOutputConverter<SkillSelection> converter = new BeanOutputConverter<>(SkillSelection.class);
            String prompt = """
                    你是技能路由器。根据用户消息从以下技能中选择最合适的一个，只输出 JSON。
                    %s
                    用户消息：%s
                    只返回 JSON，不要任何解释。""" .formatted(skillRegistry.describeAll(), userMessage);
            String json = chatClient.prompt().user(prompt).call().content();
            SkillSelection selection = converter.convert(json);
            return skillRegistry.findById(selection.getSkillId());
        } catch (Exception e) {
            log.warn("LLM 技能路由失败，退回规则选择: err={}", e.getMessage());
            return ruleBasedSelect(userMessage);
        }
    }

    private AgentSkill ruleBasedSelect(String userMessage) {
        if (containsAny(userMessage, "购物车", "下单", "订单", "取消", "支付", "结算", "买")) {
            return skillRegistry.findById("trade");
        }
        if (containsAny(userMessage, "地址", "个人信息", "我的信息", "账号", "昵称", "手机号", "资料")) {
            return skillRegistry.findById("personal");
        }
        return skillRegistry.findById(DEFAULT_SKILL_ID);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
