package com.shumamall.agent.planning;

import lombok.Data;

import java.util.List;

/**
 * 执行计划（Plan-then-Execute 模式的产物）。
 * <p>
 * 由规划模块根据用户消息 + 技能约束 + 工具目录生成，
 * 执行模块按序执行其中的 {@link PlanStep}。
 */
@Data
public class AgentPlan {

    /** 计划目标（一句话概括用户意图） */
    private String goal;

    /** 计划步骤（顺序执行） */
    private List<PlanStep> steps;
}
