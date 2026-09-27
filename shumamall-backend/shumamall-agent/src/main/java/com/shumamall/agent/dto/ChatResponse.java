package com.shumamall.agent.dto;

import com.shumamall.agent.guard.GuardResult;
import com.shumamall.agent.planning.AgentPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 对话响应 DTO。
 * <p>
 * 除最终回复外，附带本次命中的技能、生成的计划与工具执行结果，便于前端展示完整链路。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 会话 ID（后续轮次回传以继续上下文） */
    private String sessionId;

    /** 最终回复 */
    private String reply;

    /** 命中的技能 ID */
    private String skillId;

    /** 本次生成的执行计划（planEnabled 关闭时为 null） */
    private AgentPlan plan;

    /** 工具调用结果列表 */
    private List<ToolResult> toolResults;

    /**
     * 高风险操作拦截结果。
     * <p>
     * 非空且 passed=false 时表示本次请求被 Guard 拦截，前端应展示 confirmMessage 引导用户确认。
     */
    private GuardResult pendingConfirmation;
}
