package com.shumamall.agent.skill;

import com.shumamall.agent.config.AgentPromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 技能选择器：根据用户消息路由到合适的技能。
 * <p>
 * 自动模式下优先 LLM 路由（结构化输出 {@link SkillSelection}）；
 * LLM 失败或未配置真实模型时退化为关键词规则。前端显式指定的 skillId 始终优先。
 */
@Slf4j
@Component
public class SkillSelector {

    private static final String DEFAULT_SKILL_ID = "shopping_guide";

    private final SkillRegistry skillRegistry;
    private final ChatClient chatClient;
    private final AgentPromptProvider promptProvider;
    private final boolean mockLlm;

    public SkillSelector(SkillRegistry skillRegistry,
                         ChatClient chatClient,
                         AgentPromptProvider promptProvider,
                         @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm) {
        this.skillRegistry = skillRegistry;
        this.chatClient = chatClient;
        this.promptProvider = promptProvider;
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
        return select(userMessage, preferredSkillId, "");
    }

    /**
     * @param conversationContext 近期会话摘要，用于「再查一次」等短句延续订单/导购话题
     */
    public AgentSkill select(String userMessage, String preferredSkillId, String conversationContext) {
        if (preferredSkillId != null && !preferredSkillId.isBlank()) {
            AgentSkill preferred = skillRegistry.findById(preferredSkillId);
            if (preferred != null) {
                log.info("技能路由结果: skillId={} (前端指定)", preferred.getSkillId());
                return preferred;
            }
        }
        AgentSkill skill = mockLlm
                ? ruleBasedSelect(userMessage, conversationContext)
                : llmSelect(userMessage, conversationContext);
        if (skill == null) {
            skill = ruleBasedSelect(userMessage, conversationContext);
        }
        if (skill == null) {
            skill = skillRegistry.findById(DEFAULT_SKILL_ID);
        }
        log.info("技能路由结果: skillId={}", skill.getSkillId());
        return skill;
    }

    private AgentSkill llmSelect(String userMessage, String conversationContext) {
        try {
            BeanOutputConverter<SkillSelection> converter = new BeanOutputConverter<>(SkillSelection.class);
            String prompt = promptProvider.skillRouterUserTemplate(
                    skillRegistry.describeAll(),
                    formatContextBlock(conversationContext),
                    userMessage);
            String json = chatClient.prompt().user(prompt).call().content();
            SkillSelection selection = converter.convert(json);
            return skillRegistry.findById(selection.getSkillId());
        } catch (Exception e) {
            log.warn("LLM 技能路由失败，退回规则选择: err={}", e.getMessage());
            return ruleBasedSelect(userMessage, conversationContext);
        }
    }

    private AgentSkill ruleBasedSelect(String userMessage, String conversationContext) {
        if (signalsTrade(userMessage) || signalsTrade(conversationContext)) {
            return skillRegistry.findById("trade");
        }
        // personal 只看当前句，避免订单回复里的「收货地址」误触
        if (signalsPersonal(userMessage)) {
            return skillRegistry.findById("personal");
        }
        return skillRegistry.findById(DEFAULT_SKILL_ID);
    }

    private boolean signalsTrade(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsAny(text, "购物车", "加购", "加入购物车", "下单", "订单", "取消", "支付", "结算", "付款", "结账", "ORD")) {
            return true;
        }
        return text.contains("订单号") || text.contains("待付款") || text.contains("已取消");
    }

    private boolean signalsPersonal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsAny(text, "地址", "个人信息", "我的信息", "账号", "昵称", "手机号", "资料");
    }

    private String formatContextBlock(String conversationContext) {
        if (conversationContext == null || conversationContext.isBlank()) {
            return "";
        }
        return "会话上下文：\n" + conversationContext + "\n";
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
