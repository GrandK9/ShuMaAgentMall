package com.shumamall.agent.skill;

/**
 * Agent 技能抽象。
 * <p>
 * 技能 = 面向一类用户意图封装的行为能力，包含：
 * <ul>
 *   <li>技能标识与描述（供 LLM 做技能路由）</li>
 *   <li>系统提示词（约束模型在该技能下的角色、可用工具与行为边界）</li>
 * </ul>
 * 交易、个人中心走「规划 → 确认 → 执行」；导购走模型原生 function calling，
 * 见 {@link #usesNativeToolCalling()}。
 */
public interface AgentSkill {

    /**
     * 技能唯一标识（LLM 路由与注册表查找使用）。
     */
    String getSkillId();

    /**
     * 技能名称（中文，用于展示）。
     */
    String getSkillName();

    /**
     * 技能能力描述（供 LLM 判断用户意图归属哪个技能）。
     */
    String getDescription();

    /**
     * 构建该系统提示词，注入 ChatClient 的 system 消息。
     */
    String buildSystemPrompt();

    /**
     * 是否由模型在对话中直接发起工具调用（Chat API tools），而不是先产出 JSON 计划再由服务端执行。
     * <p>
     * 仅用于步骤事先不确定的只读场景（导购检索）。写操作技能保持默认 false，
     * 以便整份计划在执行前经过高风险确认。
     */
    default boolean usesNativeToolCalling() {
        return false;
    }
}
