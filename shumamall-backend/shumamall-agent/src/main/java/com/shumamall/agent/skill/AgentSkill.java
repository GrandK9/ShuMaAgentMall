package com.shumamall.agent.skill;

/**
 * Agent 技能抽象。
 * <p>
 * 技能 = 面向一类用户意图封装的行为能力，包含：
 * <ul>
 *   <li>技能标识与描述（供 LLM 做技能路由）</li>
 *   <li>系统提示词（约束模型在该技能下的角色、可用工具与行为边界）</li>
 * </ul>
 * 规划模块在技能约束的边界内生成工具调用计划，形成
 * <b>技能层 → 规划模块 → 工具调用模块</b> 的纵向分层。
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
}
