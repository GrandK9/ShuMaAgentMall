package com.shumamall.agent.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析 Nacos / 本地配置中的提示词模板，未配置时回退 {@link AgentPromptDefaults}。
 */
@Component
public class AgentPromptProvider {

    private final AgentRuntimeProperties properties;

    public AgentPromptProvider(AgentRuntimeProperties properties) {
        this.properties = properties;
    }

    public String systemPromptForSkill(String skillId) {
        String configured = properties.getPrompts().getSkills().get(skillId);
        if (StringUtils.hasText(configured)) {
            return configured.strip();
        }
        return AgentPromptDefaults.systemPromptForSkill(skillId);
    }

    public String skillRouterUserTemplate(String skillCatalog, String conversationContext, String userMessage) {
        String template = coalesce(properties.getPrompts().getSkillRouterUserTemplate(),
                AgentPromptDefaults.SKILL_ROUTER_USER_TEMPLATE);
        return template.replace("{skillCatalog}", nullToEmpty(skillCatalog))
                .replace("{conversationContext}", nullToEmpty(conversationContext))
                .replace("{userMessage}", nullToEmpty(userMessage));
    }

    public String plannerUserTemplate(String skillId,
                                        String toolCatalog,
                                        String outputFormatExample,
                                        String plannerRules,
                                        String outputSchema,
                                        String conversationContext,
                                        String userMessage) {
        String template = coalesce(properties.getPrompts().getPlannerUserTemplate(),
                AgentPromptDefaults.PLANNER_USER_TEMPLATE);
        return template.replace("{skillId}", nullToEmpty(skillId))
                .replace("{toolCatalog}", nullToEmpty(toolCatalog))
                .replace("{outputFormatExample}", nullToEmpty(outputFormatExample))
                .replace("{plannerRules}", nullToEmpty(plannerRules))
                .replace("{outputSchema}", nullToEmpty(outputSchema))
                .replace("{conversationContext}", nullToEmpty(conversationContext))
                .replace("{userMessage}", nullToEmpty(userMessage));
    }

    public String plannerRules(int maxSteps) {
        String rules = coalesce(properties.getPrompts().getPlannerRules(),
                AgentPromptDefaults.PLANNER_RULES);
        return rules.replace("{maxSteps}", String.valueOf(maxSteps));
    }

    public String plannerOutputFormatExample() {
        return coalesce(properties.getPrompts().getPlannerOutputFormatExample(), """
                {"goal": "一句话目标", "steps": [
                  {"stepId": "step-1", "description": "做什么", "toolName": "工具名", "params": {"参数名": 值}},
                  ...
                ]}""");
    }

    public String summarySystem() {
        return coalesce(properties.getPrompts().getSummarySystem(), AgentPromptDefaults.SUMMARY_SYSTEM);
    }

    public String summaryUserTemplate(String existingSummary, String fragment) {
        String template = coalesce(properties.getPrompts().getSummaryUserTemplate(),
                AgentPromptDefaults.SUMMARY_USER_TEMPLATE);
        return template.replace("{existingSummary}", nullToEmpty(existingSummary))
                .replace("{fragment}", nullToEmpty(fragment));
    }

    public String reactRethinkPrefix(String userMessage) {
        String template = coalesce(properties.getPrompts().getReactRethinkPrefix(),
                AgentPromptDefaults.REACT_RETHINK_PREFIX);
        return template.replace("{userMessage}", nullToEmpty(userMessage));
    }

    private static String coalesce(String configured, String fallback) {
        return StringUtils.hasText(configured) ? configured.strip() : fallback;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
