package com.shumamall.agent.planning;

import lombok.Data;

import java.util.Map;

/**
 * 计划步骤：单个工具调用步骤。
 * <p>
 * LLM 以结构化 JSON 输出，规划器校验后交给 {@link PlanExecutor} 执行。
 */
@Data
public class PlanStep {

    /** 步骤编号（如 step-1） */
    private String stepId;

    /** 步骤说明（人话描述这一步做什么） */
    private String description;

    /** 要调用的工具名（必须存在于工具目录中） */
    private String toolName;

    /** 工具入参（键值对，执行时序列化为 JSON 传给工具） */
    private Map<String, Object> params;

    /**
     * 待确认凭据（仅 Human-in-the-Loop 拦截时由服务端签发，前端确认后原样回传）。
     * <p>
     * 执行链路不读取该字段；凭据内容与当前 stepId / 工具 / 参数绑定，见
     * {@code ConfirmTokenService}。
     */
    private String confirmToken;
}
