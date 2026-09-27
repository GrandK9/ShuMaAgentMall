package com.shumamall.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.AgentStreamResponse;
import com.shumamall.agent.dto.ChatRequest;
import com.shumamall.agent.guard.GuardResult;
import com.shumamall.agent.guard.HighRiskActionGuard;
import com.shumamall.agent.memory.ConversationService;
import com.shumamall.agent.planning.AgentPlan;
import com.shumamall.agent.planning.PlanExecutor;
import com.shumamall.agent.planning.PlannerService;
import com.shumamall.agent.planning.PlanStep;
import com.shumamall.agent.planning.ReActPlanExecutor;
import com.shumamall.agent.skill.AgentSkill;
import com.shumamall.agent.skill.SkillSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentOrchestrator 流式链路 Human-in-the-Loop 拦截单元测试。
 * <p>
 * 验证当 Guard 判定计划存在高风险步骤且用户未确认时，流式响应携带 pendingConfirmation，
 * 供 Controller 转换为 SSE confirm 事件。
 */
class AgentOrchestratorStreamGuardTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final SkillSelector skillSelector = mock(SkillSelector.class);
    private final PlannerService plannerService = mock(PlannerService.class);
    private final PlanExecutor planExecutor = mock(PlanExecutor.class);
    private final ReActPlanExecutor reActPlanExecutor = mock(ReActPlanExecutor.class);
    private final HighRiskActionGuard highRiskActionGuard = mock(HighRiskActionGuard.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** chatStream 内部由 chatClient.mutate() 派生出的技能客户端（setUp 中装配） */
    private ChatClient skillClient;

    private final AgentOrchestrator orchestrator = new AgentOrchestrator(
            conversationService, skillSelector, plannerService, planExecutor, reActPlanExecutor,
            highRiskActionGuard, chatClient, chatMemory, objectMapper);

    @BeforeEach
    void setUp() {
        // chatStream 在规划前会先构建 skillClient，这里 mock 掉 ChatClient 的链式构建
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        skillClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(skillClient);
        when(chatClient.mutate()).thenReturn(builder);
    }

    @Test
    void chatStream_whenGuardBlocked_withoutConfirmedSteps_returnsPendingConfirmation() {
        // given
        ChatRequest request = new ChatRequest();
        request.setMessage("帮我直接下单 iPhone 16");
        request.setSessionId("sess-001");
        request.setPlanEnabled(true);
        request.setConfirmTokens(Set.of());

        AgentSkill skill = mockSkill("shopping_guide");
        when(conversationService.getOrCreateSession("sess-001", 1L)).thenReturn("sess-001");
        when(skillSelector.select("帮我直接下单 iPhone 16", null)).thenReturn(skill);

        PlanStep step = new PlanStep();
        step.setStepId("step-1");
        step.setDescription("调用 createOrder 下单");
        step.setToolName("createOrder");
        step.setParams(Map.of("productId", 1));
        AgentPlan plan = new AgentPlan();
        plan.setGoal("完成 iPhone 16 下单");
        plan.setSteps(List.of(step));
        when(plannerService.plan("帮我直接下单 iPhone 16", skill)).thenReturn(plan);

        GuardResult blocked = GuardResult.block(List.of(step),
                "检测到下单操作，请确认是否继续？");
        when(highRiskActionGuard.check(plan, Set.of(), "sess-001")).thenReturn(blocked);

        // when
        AgentStreamResponse response = orchestrator.chatStream(request, 1L);

        // then
        assertThat(response.getSessionId()).isEqualTo("sess-001");
        assertThat(response.getSkillId()).isEqualTo("shopping_guide");
        assertThat(response.getPlan()).isEqualTo(plan);
        assertThat(response.getPendingConfirmation())
                .isNotNull()
                .satisfies(guard -> {
                    assertThat(guard.isPassed()).isFalse();
                    assertThat(guard.getConfirmMessage()).isEqualTo("检测到下单操作，请确认是否继续？");
                    assertThat(guard.getPendingSteps()).hasSize(1);
                    assertThat(guard.getPendingSteps().get(0).getStepId()).isEqualTo("step-1");
                });
        assertThat(response.getContent()).isNull();
    }

    @Test
    void chatStream_whenGuardBlocked_withConfirmedSteps_returnsPendingConfirmationOnlyForUnconfirmed() {
        // given
        ChatRequest request = new ChatRequest();
        request.setMessage("帮我直接下单 iPhone 16");
        request.setSessionId("sess-002");
        request.setPlanEnabled(true);
        request.setConfirmTokens(Set.of("tok-step-1"));

        AgentSkill skill = mockSkill("shopping_guide");
        when(conversationService.getOrCreateSession("sess-002", 1L)).thenReturn("sess-002");
        when(skillSelector.select("帮我直接下单 iPhone 16", null)).thenReturn(skill);

        PlanStep step1 = new PlanStep();
        step1.setStepId("step-1");
        step1.setDescription("调用 createOrder 下单");
        step1.setToolName("createOrder");
        step1.setParams(Map.of("productId", 1));
        PlanStep step2 = new PlanStep();
        step2.setStepId("step-2");
        step2.setDescription("调用 payOrder 支付");
        step2.setToolName("payOrder");
        step2.setParams(Map.of("orderNo", "O123"));
        AgentPlan plan = new AgentPlan();
        plan.setGoal("完成 iPhone 16 下单并支付");
        plan.setSteps(List.of(step1, step2));
        when(plannerService.plan("帮我直接下单 iPhone 16", skill)).thenReturn(plan);

        GuardResult blocked = GuardResult.block(List.of(step2),
                "检测到支付操作，请确认是否继续？");
        when(highRiskActionGuard.check(plan, Set.of("tok-step-1"), "sess-002")).thenReturn(blocked);

        // when
        AgentStreamResponse response = orchestrator.chatStream(request, 1L);

        // then
        assertThat(response.getPendingConfirmation()).isNotNull();
        assertThat(response.getPendingConfirmation().isPassed()).isFalse();
        assertThat(response.getPendingConfirmation().getPendingSteps())
                .hasSize(1)
                .extracting(PlanStep::getStepId)
                .containsExactly("step-2");
    }

    @Test
    void chatStream_whenConfirmedResend_usesCachedPlanInsteadOfReplanning() {
        // given：用户点「确认执行」后重发同一条消息，并带上服务端签发的凭据
        ChatRequest request = new ChatRequest();
        request.setMessage("帮我直接下单 iPhone 16");
        request.setSessionId("sess-003");
        request.setPlanEnabled(true);
        request.setConfirmTokens(Set.of("tok-step-1"));

        AgentSkill skill = mockSkill("shopping_guide");
        when(conversationService.getOrCreateSession("sess-003", 1L)).thenReturn("sess-003");
        when(skillSelector.select("帮我直接下单 iPhone 16", null)).thenReturn(skill);

        PlanStep step = new PlanStep();
        step.setStepId("step-1");
        step.setDescription("调用 createOrder 下单");
        step.setToolName("createOrder");
        step.setParams(Map.of("productId", 1));
        AgentPlan cached = new AgentPlan();
        cached.setGoal("完成 iPhone 16 下单");
        cached.setSteps(List.of(step));
        // 上一轮拦截时缓存下来、也就是用户看到并确认过的那一份计划
        when(highRiskActionGuard.loadPendingPlan("sess-003", "帮我直接下单 iPhone 16")).thenReturn(cached);
        when(highRiskActionGuard.check(cached, Set.of("tok-step-1"), "sess-003")).thenReturn(GuardResult.pass());

        // 放行后走到回复生成：装配流式链路的最小 mock（content 为空即可，本用例只关心规划与执行）
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(skillClient.prompt()).thenReturn(spec);
        when(spec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        // when
        AgentStreamResponse response = orchestrator.chatStream(request, 1L);

        // then：执行的就是缓存里那份计划，规划器不再被调用（否则模型抖动会让凭据摘要失配、被迫二次确认）
        assertThat(response.getPlan()).isSameAs(cached);
        verify(plannerService, never()).plan(any(), any());
        verify(highRiskActionGuard).forgetPendingPlan("sess-003");
    }

    @Test
    void pendingConfirmation_serializesToConfirmEventJson() throws Exception {
        // given：模拟 Controller 中 confirm 事件的 map 结构
        PlanStep step = new PlanStep();
        step.setStepId("step-1");
        step.setDescription("调用 createOrder 下单");
        step.setToolName("createOrder");
        step.setParams(Map.of("productId", 1));
        GuardResult guard = GuardResult.block(List.of(step), "确认下单吗？");

        Map<String, Object> confirm = new java.util.HashMap<>();
        confirm.put("pendingSteps", guard.getPendingSteps());
        confirm.put("confirmMessage", guard.getConfirmMessage());

        // when
        String json = objectMapper.writeValueAsString(confirm);

        // then：前端 confirm 事件依赖这两个字段
        assertThat(json)
                .contains("\"confirmMessage\":\"确认下单吗？\"")
                .contains("\"stepId\":\"step-1\"")
                .contains("\"toolName\":\"createOrder\"");
    }

    private AgentSkill mockSkill(String skillId) {
        AgentSkill skill = mock(AgentSkill.class);
        when(skill.getSkillId()).thenReturn(skillId);
        when(skill.buildSystemPrompt()).thenReturn("你是电商导购助手");
        return skill;
    }
}
