package com.shumamall.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.AgentStreamResponse;
import com.shumamall.agent.dto.ChatRequest;
import com.shumamall.agent.dto.ChatResponse;
import com.shumamall.agent.dto.ToolResult;
import com.shumamall.agent.guard.GuardResult;
import com.shumamall.agent.guard.HighRiskActionGuard;
import com.shumamall.agent.memory.ConversationService;
import com.shumamall.agent.planning.AgentPlan;
import com.shumamall.agent.planning.PlanExecutor;
import com.shumamall.agent.planning.PlannerService;
import com.shumamall.agent.planning.ReActPlanExecutor;
import com.shumamall.agent.skill.AgentSkill;
import com.shumamall.agent.skill.SkillSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 编排器（Orchestrator）：一次对话请求的主链路调度。
 * <p>
 * 编排顺序（对应 Agent 四大模块的纵向组合）：
 * <ol>
 *   <li><b>会话/记忆</b>：{@link ConversationService} 获取/创建会话并校验归属，
 *        {@link MessageChatMemoryAdvisor} 自动注入历史并写回本轮</li>
 *   <li><b>技能层</b>：{@link SkillSelector} 将消息路由到具体 {@link AgentSkill}，
 *        技能提供系统提示词约束模型行为边界</li>
 *   <li><b>导购</b>：真实模型走 {@link ShoppingGuideChat} 原生 function calling（只读检索工具）。
 *        mock 模型不会发起 tool call，仍退回规划执行，保证本地演示能搜到商品</li>
 *   <li><b>交易 / 个人中心</b>：{@link PlannerService} 生成计划，高风险步骤经
 *        {@link HighRiskActionGuard} 确认后由 {@link PlanExecutor} 执行</li>
 *   <li><b>回复生成</b>：规划路径把工具结果回填给 LLM；导购路径的回复由工具循环直接产生</li>
 * </ol>
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private final ConversationService conversationService;
    private final SkillSelector skillSelector;
    private final PlannerService plannerService;
    private final PlanExecutor planExecutor;
    private final ReActPlanExecutor reActPlanExecutor;
    private final HighRiskActionGuard highRiskActionGuard;
    private final ShoppingGuideChat shoppingGuideChat;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;
    private final boolean mockLlm;

    public AgentOrchestrator(ConversationService conversationService,
                             SkillSelector skillSelector,
                             PlannerService plannerService,
                             PlanExecutor planExecutor,
                             ReActPlanExecutor reActPlanExecutor,
                             HighRiskActionGuard highRiskActionGuard,
                             ShoppingGuideChat shoppingGuideChat,
                             ChatClient chatClient,
                             ChatMemory chatMemory,
                             ObjectMapper objectMapper,
                             @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm) {
        this.conversationService = conversationService;
        this.skillSelector = skillSelector;
        this.plannerService = plannerService;
        this.planExecutor = planExecutor;
        this.reActPlanExecutor = reActPlanExecutor;
        this.highRiskActionGuard = highRiskActionGuard;
        this.shoppingGuideChat = shoppingGuideChat;
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.objectMapper = objectMapper;
        this.mockLlm = mockLlm;
    }

    /**
     * 处理一次对话请求。
     *
     * @param request 对话请求（sessionId / message / skillId / planEnabled）
     * @param userId  当前登录用户（SecurityContext 提供）
     * @return 对话响应（回复 + 技能 + 计划 + 工具结果，便于前端展示完整链路）
     */
    public ChatResponse chat(ChatRequest request, Long userId) {
        long start = System.currentTimeMillis();

        // 1. 会话/记忆：获取或创建会话，校验归属
        String sessionId = conversationService.getOrCreateSession(request.getSessionId(), userId);

        // 2. 技能路由（带会话上下文，避免「再查一次」等短句误判为导购）
        String routingContext = conversationService.buildPlanningContext(sessionId, 12);
        AgentSkill skill = skillSelector.select(request.getMessage(), request.getSkillId(), routingContext);

        // 3. 构建带技能系统提示词 + 记忆 Advisor 的客户端（每请求派生，互不影响）
        ChatClient skillClient = chatClient.mutate()
                .defaultSystem(skill.buildSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // 4. 导购：原生工具循环。交易 / 个人中心：规划 + 确认 + 执行
        boolean planEnabled = request.getPlanEnabled() == null || request.getPlanEnabled();
        AgentPlan plan = null;
        List<ToolResult> toolResults = List.of();
        GuardResult guardResult = null;
        String reply;
        if (useNativeToolCalling(skill, planEnabled)) {
            ShoppingGuideChat.Outcome outcome = shoppingGuideChat.complete(
                    skillClient, sessionId, request.getMessage(), userId);
            reply = outcome.reply();
            toolResults = outcome.toolResults();
        } else if (planEnabled) {
            plan = resolvePlan(request, skill, sessionId);
            // 4.5 Human-in-the-Loop：高风险操作二次确认（凭据由服务端签发并绑定会话与操作内容）
            guardResult = highRiskActionGuard.check(plan, request.getConfirmTokens(), sessionId);
            if (!guardResult.isPassed()) {
                // 记住这份计划：用户确认后执行的就是它，不再重新规划（否则凭据摘要会因模型输出抖动而失配）
                highRiskActionGuard.rememberPendingPlan(sessionId, request.getMessage(), plan);
                rememberTurn(sessionId, request.getMessage(), guardResult.getConfirmMessage());
                log.info("高风险操作被拦截，等待用户确认: sessionId={}, steps={}", sessionId,
                        guardResult.getPendingSteps().stream().map(com.shumamall.agent.planning.PlanStep::getStepId).toList());
                return ChatResponse.builder()
                        .sessionId(sessionId)
                        .reply(guardResult.getConfirmMessage())
                        .skillId(skill.getSkillId())
                        .plan(plan)
                        .toolResults(toolResults)
                        .pendingConfirmation(guardResult)
                        .build();
            }
            highRiskActionGuard.forgetPendingPlan(sessionId);
            toolResults = executePlan(plan, request, skill, userId, sessionId);
            // 5. 回复生成：工具结果回填给 LLM；记忆只存真实用户句 + 助手回复（见 rememberTurn）
            String context = buildToolContext(request.getMessage(), toolResults);
            reply = replySynthesisClient(skill).prompt().user(context).call().content();
            rememberTurn(sessionId, request.getMessage(), reply);
        } else {
            reply = skillClient.prompt().user(request.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call().content();
        }

        log.info("对话处理完成: sessionId={}, skillId={}, planSteps={}, cost={}ms",
                sessionId, skill.getSkillId(), plan == null ? 0 : plan.getSteps().size(),
                System.currentTimeMillis() - start);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .reply(reply)
                .skillId(skill.getSkillId())
                .plan(plan)
                .toolResults(toolResults)
                .pendingConfirmation(guardResult)
                .build();
    }

    /**
     * 处理一次对话请求（流式版）。
     * <p>
     * 编排前四步（会话/记忆 → 技能路由 → 规划 → 工具执行）与 {@link #chat} 完全一致，
     * 仅最后一步"回复生成"改用 {@code ChatClient.stream()} 返回 token 流，
     * 由 Controller 以 SSE 方式逐块推送，前端可实现打字机效果。
     *
     * @param request 对话请求（sessionId / message / skillId / planEnabled）
     * @param userId  当前登录用户（SecurityContext 提供）
     * @return 流式对话响应（会话信息 + 规划 + 工具结果 + 回复 token 流）
     */
    public AgentStreamResponse chatStream(ChatRequest request, Long userId) {
        // 1. 会话/记忆：获取或创建会话，校验归属
        String sessionId = conversationService.getOrCreateSession(request.getSessionId(), userId);

        // 2. 技能路由（带会话上下文，避免「再查一次」等短句误判为导购）
        String routingContext = conversationService.buildPlanningContext(sessionId, 12);
        AgentSkill skill = skillSelector.select(request.getMessage(), request.getSkillId(), routingContext);

        // 3. 构建带技能系统提示词 + 记忆 Advisor 的客户端（每请求派生，互不影响）
        ChatClient skillClient = chatClient.mutate()
                .defaultSystem(skill.buildSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // 4. 导购 native：stream + tool 事件；交易 / 个人中心：计划执行后 stream 生成回复
        boolean planEnabled = request.getPlanEnabled() == null || request.getPlanEnabled();
        AgentPlan plan = null;
        List<ToolResult> toolResults = List.of();
        Flux<ToolResult> toolEventFlux = null;
        Boolean incrementalToolStream = null;
        Flux<String> content;
        if (useNativeToolCalling(skill, planEnabled)) {
            Sinks.Many<ToolResult> toolSink = Sinks.many().unicast().onBackpressureBuffer();
            incrementalToolStream = true;
            toolResults = List.of();
            content = shoppingGuideChat.streamTokens(
                            skillClient, sessionId, request.getMessage(), userId,
                            tr -> toolSink.tryEmitNext(tr))
                    .doFinally(signal -> toolSink.tryEmitComplete());
            toolEventFlux = toolSink.asFlux();
        } else if (planEnabled) {
            plan = resolvePlan(request, skill, sessionId);
            // 4.5 Human-in-the-Loop：高风险操作二次确认（凭据由服务端签发并绑定会话与操作内容）
            GuardResult guardResult = highRiskActionGuard.check(plan, request.getConfirmTokens(), sessionId);
            if (!guardResult.isPassed()) {
                // 记住这份计划：用户确认后执行的就是它，不再重新规划（否则凭据摘要会因模型输出抖动而失配）
                highRiskActionGuard.rememberPendingPlan(sessionId, request.getMessage(), plan);
                rememberTurn(sessionId, request.getMessage(), guardResult.getConfirmMessage());
                log.info("流式请求高风险操作被拦截: sessionId={}", sessionId);
                return AgentStreamResponse.builder()
                        .sessionId(sessionId)
                        .skillId(skill.getSkillId())
                        .plan(plan)
                        .toolResults(toolResults)
                        .pendingConfirmation(guardResult)
                        .build();
            }
            highRiskActionGuard.forgetPendingPlan(sessionId);
            toolResults = executePlan(plan, request, skill, userId, sessionId);
            String context = buildToolContext(request.getMessage(), toolResults);
            AtomicReference<StringBuilder> replyBuffer = new AtomicReference<>(new StringBuilder());
            content = replySynthesisClient(skill).prompt().user(context)
                    .stream()
                    .content()
                    .doOnNext(chunk -> replyBuffer.get().append(chunk))
                    .doOnComplete(() -> rememberTurn(sessionId, request.getMessage(), replyBuffer.get().toString()));
        } else {
            content = skillClient.prompt().user(request.getMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream().content();
        }

        log.info("流式对话请求: sessionId={}, skillId={}, planSteps={}",
                sessionId, skill.getSkillId(), plan == null ? 0 : plan.getSteps().size());

        return AgentStreamResponse.builder()
                .sessionId(sessionId)
                .skillId(skill.getSkillId())
                .plan(plan)
                .toolResults(toolResults)
                .incrementalToolStream(incrementalToolStream)
                .toolEventFlux(toolEventFlux)
                .content(content)
                .build();
    }

    /**
     * 导购在真实模型下走原生 function calling。
     * mock 模型只回显文本、不会发起 tool call，此时仍走规划，本地演示才能调到检索工具。
     */
    private boolean useNativeToolCalling(AgentSkill skill, boolean planEnabled) {
        return planEnabled && skill.usesNativeToolCalling() && !mockLlm;
    }

    /**
     * 规划路径的回复合成：不挂 Memory Advisor，避免把 {@link #buildToolContext} 整段写入 Redis。
     */
    private ChatClient replySynthesisClient(AgentSkill skill) {
        return chatClient.mutate()
                .defaultSystem(skill.buildSystemPrompt())
                .build();
    }

    private void rememberTurn(String sessionId, String userMessage, String assistantReply) {
        if (!StringUtils.hasText(userMessage) || !StringUtils.hasText(assistantReply)) {
            return;
        }
        chatMemory.add(sessionId, List.of(new UserMessage(userMessage), new AssistantMessage(assistantReply)));
    }

    private String buildToolContext(String userMessage, List<ToolResult> toolResults) {
        try {
            String toolResultsJson = objectMapper.writeValueAsString(toolResults);
            return "用户消息：%s%n%n以下是工具执行结果，请据此组织回复（无结果时说明原因）：%n%s"
                    .formatted(userMessage, toolResultsJson);
        } catch (Exception e) {
            log.warn("工具结果序列化失败，仅回填用户消息: err={}", e.getMessage());
            return userMessage;
        }
    }

    /**
     * 生成本次请求要执行的计划。
     * <p>
     * 带确认凭据重发时，优先复用「用户看到并确认过的那份计划」（见
     * {@link HighRiskActionGuard#rememberPendingPlan}）：确认凭据绑定的是 stepId + 工具 + 参数摘要，
     * 而重新规划的结果未必逐字相同（模型会多吐取值为 null / 0 的占位参数），摘要失配会让
     * 已经确认过的操作被要求再确认一次。缓存未命中（超过有效期 / 消息不同 / Redis 异常）时
     * 退化为正常规划，行为与改动前一致。
     */
    private AgentPlan resolvePlan(ChatRequest request, AgentSkill skill, String sessionId) {
        if (!CollectionUtils.isEmpty(request.getConfirmTokens())) {
            AgentPlan pending = highRiskActionGuard.loadPendingPlan(sessionId, request.getMessage());
            if (pending != null) {
                log.info("复用用户已确认的计划: sessionId={}, steps={}", sessionId, pending.getSteps().size());
                return pending;
            }
        }
        String planningContext = conversationService.buildPlanningContext(sessionId, 12);
        return plannerService.plan(request.getMessage(), skill, planningContext);
    }

    /**
     * 执行计划：根据 reactEnabled 选择单次执行或 ReAct 多轮执行。
     */
    private List<ToolResult> executePlan(AgentPlan plan, ChatRequest request, AgentSkill skill, Long userId,
                                        String sessionId) {
        boolean reactEnabled = request.getReactEnabled() != null && request.getReactEnabled();
        String planningContext = conversationService.buildPlanningContext(sessionId, 12);
        if (reactEnabled) {
            return reActPlanExecutor.execute(plan, request.getMessage(), skill, userId, planningContext);
        }
        return planExecutor.execute(plan, userId);
    }
}
