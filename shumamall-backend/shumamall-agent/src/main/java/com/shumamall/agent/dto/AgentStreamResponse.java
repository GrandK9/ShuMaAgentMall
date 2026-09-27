package com.shumamall.agent.dto;

import com.shumamall.agent.guard.GuardResult;
import com.shumamall.agent.planning.AgentPlan;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式对话响应（SSE）。
 * <p>
 * 与 {@link ChatResponse} 的区别：{@code content} 是 LLM 回复的 token 流
 * （{@link Flux}），编排阶段（技能路由/规划/工具执行）仍在请求内同步完成，
 * 仅最后一步"回复生成"改为流式输出，保证工具调用结果完整可见。
 */
@Data
@Builder
public class AgentStreamResponse {

    /** 会话 ID（记忆模块隔离键） */
    private String sessionId;

    /** 命中的技能 ID */
    private String skillId;

    /** 规划结果（Plan-then-Execute），未开启规划时为 null */
    private AgentPlan plan;

    /** 工具执行结果列表 */
    private List<ToolResult> toolResults;

    /**
     * Human-in-the-Loop 拦截结果。
     * <p>
     * 非空且未通过时，Controller 优先发送 {@code confirm} SSE 事件，不进入 token 流。
     */
    private GuardResult pendingConfirmation;

    /** LLM 回复内容流（逐 token 推送） */
    private Flux<String> content;
}
