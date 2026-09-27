package com.shumamall.agent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.AgentStreamResponse;
import com.shumamall.agent.dto.ChatRequest;
import com.shumamall.agent.dto.ChatResponse;
import com.shumamall.agent.dto.ChatSessionSummary;
import com.shumamall.agent.memory.ConversationService;
import com.shumamall.agent.service.AgentOrchestrator;
import com.shumamall.agent.skill.AgentSkill;
import com.shumamall.agent.skill.SkillRegistry;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.chat.messages.Message;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Agent 对话入口。
 * <p>
 * {@code POST /api/v1/agent/chat} 为唯一对话入口（需登录，TokenFilter 拦截）；
 * {@code GET /api/v1/agent/skills} 供前端展示/指定可用技能。
 */
@Tag(name = "智能体-对话", description = "购物智能体的对话、流式对话、技能列表与会话记忆管理接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final SkillRegistry skillRegistry;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final ExecutorService agentExecutor;

    public AgentChatController(AgentOrchestrator agentOrchestrator,
                               SkillRegistry skillRegistry,
                               ConversationService conversationService,
                               ObjectMapper objectMapper,
                               ExecutorService agentExecutor) {
        this.agentOrchestrator = agentOrchestrator;
        this.skillRegistry = skillRegistry;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 对话入口：一次请求完成 技能路由 → 规划 → 工具执行 → 回复生成。
     */
    @Operation(summary = "对话入口：一次请求完成 技能路由 → 规划 → 工具执行 → 回复生成")
    @PostMapping("/chat")
    public R<ChatResponse> chat(@RequestBody @Valid ChatRequest request) {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return R.failed(ResultCode.UNAUTHORIZED);
        }
        log.info("收到对话请求: sessionId={}, skillId={}, planEnabled={}",
                request.getSessionId(), request.getSkillId(), request.getPlanEnabled());
        return R.ok(agentOrchestrator.chat(request, userId));
    }

    /**
     * 对话入口（流式版，SSE）。
     * <p>
     * 与 {@link #chat} 相同的编排链路，但回复以 token 流逐块推送：
     * <ol>
     *   <li><b>init 事件</b>：请求受理后立即推送 sessionId，前端展示"思考中"</li>
     *   <li><b>meta 事件</b>：一次推送 sessionId / skillId / plan / toolResults（工具调用过程先于文本可见）</li>
     *   <li><b>token 事件</b>：LLM 回复逐 token 推送，前端打字机效果</li>
     * </ol>
     * 所有事件的 {@code data} 都是 JSON 文本（token 为 JSON 字符串，见 {@link #safeSend}），
     * 这是为了让换行与首空格能逐字还原，原因见 {@link #safeSendNamedEvent}。
     * 规划、工具执行与 LLM 订阅在 {@code agentExecutor} 线程池中异步执行，
     * Tomcat 请求线程在发送 init 事件后迅速归还，避免长时间占用。
     * 请求头同样需要 {@code Authorization: Bearer <token>}（TokenFilter 拦截同路径）。
     */
    @Operation(summary = "对话入口（流式版，SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody @Valid ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            emitter.completeWithError(new IllegalStateException("未登录"));
            return emitter;
        }

        try {
            // 先发送 init 事件，让前端立即得到反馈（sessionId 由编排器实际生成后会再次通过 meta 下发）
            String pendingSessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                    ? "pending"
                    : request.getSessionId();
            emitter.send(SseEmitter.event().name("init").data(objectMapper.writeValueAsString(
                    Map.of("sessionId", pendingSessionId, "status", "planning"))));
        } catch (IOException e) {
            log.warn("发送 SSE init 事件失败: {}", e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        emitter.onTimeout(() -> emitter.completeWithError(new IOException("SSE 超时")));

        // 请求线程上先捕获上下文：RequestContextHolder 与 MDC 都是 ThreadLocal，
        // 异步线程池中的执行拿不到，会导致工具内 Feign 调用丢失 Authorization（下游 401）
        // 与 traceId（断链日志）
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String traceId = MDC.get("traceId");

        agentExecutor.submit(() -> {
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes);
            }
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            try {
                AgentStreamResponse streamResp = agentOrchestrator.chatStream(request, userId);

                // 1. meta 事件：工具调用过程（规划 + 执行结果）先于文本推送
                Map<String, Object> meta = new HashMap<>();
                meta.put("sessionId", streamResp.getSessionId());
                meta.put("skillId", streamResp.getSkillId());
                meta.put("plan", streamResp.getPlan());
                meta.put("toolResults", streamResp.getToolResults());
                safeSendNamedEvent(emitter, "meta", objectMapper.writeValueAsString(meta));

                // 2. confirm 事件：Human-in-the-Loop 高风险操作拦截，优先于 token 流
                if (streamResp.getPendingConfirmation() != null && !streamResp.getPendingConfirmation().isPassed()) {
                    Map<String, Object> confirm = new HashMap<>();
                    confirm.put("pendingSteps", streamResp.getPendingConfirmation().getPendingSteps());
                    confirm.put("confirmMessage", streamResp.getPendingConfirmation().getConfirmMessage());
                    safeSendNamedEvent(emitter, "confirm", objectMapper.writeValueAsString(confirm));
                    emitter.complete();
                    return;
                }

                // 3. token 事件：订阅 LLM 回复流，逐块推送；客户端断开时取消订阅
                Disposable subscription = streamResp.getContent().subscribe(
                        token -> safeSend(emitter, token),
                        err -> {
                            log.error("流式对话异常: err={}", err.getMessage());
                            emitter.completeWithError(err);
                        },
                        emitter::complete
                );
                emitter.onCompletion(subscription::dispose);
            } catch (Exception e) {
                log.error("流式对话启动失败: err={}", e.getMessage());
                safeSendNamedEvent(emitter, "error", "流式处理失败: " + e.getMessage());
                emitter.completeWithError(e);
            } finally {
                // 线程池复用，必须清理，避免上下文泄漏到下一个任务
                MDC.remove("traceId");
                RequestContextHolder.resetRequestAttributes();
            }
        });

        return emitter;
    }

    /** SSE 连接超时：LLM 流式生成通常 30-60s，放宽到 180s */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    /**
     * 安全发送单个 token：客户端已断开时 SseEmitter 抛 IOException，直接忽略
     * （订阅方会通过 onCompletion/onError 收尾，无需额外处理）。
     * <p>
     * token 以 JSON 字符串下发，与 init / meta / confirm 统一为「data 即 JSON」，原因见
     * {@link #safeSendNamedEvent}。
     */
    private void safeSend(SseEmitter emitter, String token) {
        try {
            safeSendNamedEvent(emitter, "token", objectMapper.writeValueAsString(token));
        } catch (JsonProcessingException e) {
            // 序列化 String 不会失败，兜底记日志并跳过该块，避免整条流被打断
            log.warn("序列化 token 失败，跳过该块: {}", e.getMessage());
        }
    }

    /**
     * 安全发送指定事件名：客户端已断开时忽略异常，避免异步线程被未处理异常中断。
     * <p>
     * <b>data 一律是 JSON 文本，不要直接传裸文本</b>：{@code SseEmitter} 会把值原样拼在
     * {@code data:} 后面（不转义、不拆行），而接收方按 SSE 规范解析时有两处会丢信息：
     * <ol>
     *   <li><b>换行被吃掉</b>：值里的换行会变成帧里的换行，接收方按空行分帧时把它当成帧结束符 ——
     *       实测 markdown 多行回复被压成一行（流式拼接文本 0 个换行，而会话记忆里的原文有 2 个）。</li>
     *   <li><b>首个空格被吃掉</b>：SSE 规定「字段值若以单个空格开头，读取方需去掉这一个空格」，
     *       而 {@code SseEmitter} 不写这个分隔空格，于是读取方去空格时吃掉的是值自身的首个空格 ——
     *       实测气泡里出现 {@code 已为你下单成功✅订单信息}（LLM 常把空格单独作为一个 token）。</li>
     * </ol>
     * JSON 序列化后换行成为 {@code \n} 转义、首字符是 {@code "}，两种情况都不再发生，
     * 接收方 {@code JSON.parse} 即可逐字还原。
     */
    private void safeSendNamedEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.debug("客户端断开 SSE 连接: {}", e.getMessage());
        }
    }

    /**
     * 列出当前可用的全部技能（供前端选择或展示）。
     */
    @Operation(summary = "列出当前可用的全部技能（供前端选择或展示）")
    @GetMapping("/skills")
    public R<List<Map<String, String>>> skills() {
        List<Map<String, String>> skills = skillRegistry.all().stream()
                .map(s -> Map.of(
                        "skillId", s.getSkillId(),
                        "skillName", s.getSkillName(),
                        "description", s.getDescription()))
                .toList();
        return R.ok(skills);
    }

    /**
     * 查询当前用户的会话历史消息。
     * <p>
     * 仅允许查询归属于当前用户的会话，防止越权读取他人记忆。
     */
    @Operation(summary = "查询当前用户的会话历史消息")
    @GetMapping("/chat/history")
    public R<List<Map<String, Object>>> chatHistory(
            @Parameter(description = "会话 ID") @RequestParam("sessionId") String sessionId) {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return R.failed(ResultCode.UNAUTHORIZED);
        }
        if (sessionId == null || sessionId.isBlank()) {
            return R.ok(List.of());
        }
        // 会话归属校验：防止越权读取他人会话
        if (!conversationService.isOwner(sessionId, userId)) {
            return R.ok(List.of());
        }
        List<Message> messages = conversationService.findHistory(sessionId);
        List<Map<String, Object>> items = messages.stream()
                .<Map<String, Object>>map(m -> Map.of(
                        "role", m.getMessageType().name().toLowerCase(),
                        "content", m.getText()))
                .toList();
        return R.ok(items);
    }

    /**
     * 查询当前用户的会话列表摘要。
     */
    @Operation(summary = "查询当前用户的会话列表摘要")
    @GetMapping("/chat/sessions")
    public R<List<ChatSessionSummary>> chatSessions() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return R.failed(ResultCode.UNAUTHORIZED);
        }
        return R.ok(conversationService.findSessions(userId));
    }

    /**
     * 删除指定会话及其记忆。
     * <p>
     * 仅允许删除归属于当前用户的会话。
     */
    @Operation(summary = "删除指定会话及其记忆")
    @DeleteMapping("/chat/session")
    public R<Void> deleteChatSession(
            @Parameter(description = "会话 ID") @RequestParam("sessionId") String sessionId) {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return R.failed(ResultCode.UNAUTHORIZED);
        }
        if (sessionId == null || sessionId.isBlank()) {
            return R.ok();
        }
        if (!conversationService.isOwner(sessionId, userId)) {
            return R.ok();
        }
        conversationService.clearSession(sessionId);
        return R.ok();
    }
}
