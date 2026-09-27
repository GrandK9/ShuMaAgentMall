package com.shumamall.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

/**
 * Agent 对话请求 DTO。
 * <p>
 * 由前端（PC 用户端 / 小程序）调用 {@code POST /api/v1/agent/chat} 时传入。
 */
@Data
public class ChatRequest {

    /** 会话 ID（为空则服务端新建会话） */
    private String sessionId;

    /** 用户消息 */
    @NotBlank(message = "消息不能为空")
    private String message;

    /** 指定技能 ID（为空则由 LLM 自动选择） */
    private String skillId;

    /** 是否开启规划模块（默认 true，执行 Plan-then-Execute） */
    private Boolean planEnabled;

    /**
     * 已确认执行的高风险步骤凭据。
     * <p>
     * Human-in-the-Loop 场景：首次请求为空，Guard 拦截后随 SSE {@code confirm} 事件下发
     * 服务端签发的一次性凭据，用户确认后由前端原样回传。凭据已绑定会话与具体操作内容，
     * 且校验通过即作废，无法伪造或重放；不接受调用方自行声明的 stepId。
     */
    private Set<String> confirmTokens;

    /**
     * 是否开启 ReAct 多轮工具调用（默认 false）。
     * <p>
     * 开启后，当首轮工具结果为空/失败时，Agent 会自动重规划并再次执行，最多 max-rounds 轮。
     */
    private Boolean reactEnabled;
}
