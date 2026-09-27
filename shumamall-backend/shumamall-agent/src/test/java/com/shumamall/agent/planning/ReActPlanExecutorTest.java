package com.shumamall.agent.planning;

import com.shumamall.agent.dto.ToolResult;
import com.shumamall.agent.skill.AgentSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReAct 多轮工具调用执行器单测。
 */
@ExtendWith(MockitoExtension.class)
class ReActPlanExecutorTest {

    @Mock
    private PlannerService plannerService;

    @Mock
    private PlanExecutor planExecutor;

    @Mock
    private AgentSkill skill;

    private ReActPlanExecutor executor;

    @BeforeEach
    void setUp() {
        // maxRounds=2
        executor = new ReActPlanExecutor(plannerService, planExecutor, 2);
    }

    private AgentPlan plan(String... toolNames) {
        AgentPlan plan = new AgentPlan();
        plan.setGoal("test");
        plan.setSteps(java.util.Arrays.stream(toolNames)
                .map(tn -> {
                    PlanStep s = new PlanStep();
                    s.setStepId("step-" + tn);
                    s.setToolName(tn);
                    s.setDescription(tn);
                    return s;
                }).toList());
        return plan;
    }

    private ToolResult result(String toolName, String status, String result) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(status)
                .result(result)
                .build();
    }

    @Test
    void execute_首轮成功_不再重规划() {
        AgentPlan initial = plan("searchProduct");
        when(planExecutor.execute(initial, 1L))
                .thenReturn(List.of(result("searchProduct", "SUCCESS", "[{\"id\":1}]")));

        List<ToolResult> results = executor.execute(initial, "手机", skill, 1L);

        assertThat(results).hasSize(1);
        verify(plannerService, times(0)).plan(any(), any());
    }

    @Test
    void execute_首轮空结果_触发重规划并执行第二轮() {
        AgentPlan initial = plan("hybridSearchProduct");
        AgentPlan retry = plan("searchProduct");
        when(planExecutor.execute(initial, 1L)).thenReturn(List.of());
        when(plannerService.plan(any(String.class), eq(skill))).thenReturn(retry);
        when(planExecutor.execute(retry, 1L))
                .thenReturn(List.of(result("searchProduct", "SUCCESS", "[{\"id\":1}]")));

        List<ToolResult> results = executor.execute(initial, "手机", skill, 1L);

        assertThat(results).hasSize(1);
        verify(plannerService, times(1)).plan(any(String.class), eq(skill));
        verify(planExecutor, times(1)).execute(retry, 1L);
    }

    @Test
    void execute_首轮全部失败_触发重规划() {
        AgentPlan initial = plan("hybridSearchProduct");
        AgentPlan retry = plan("searchProduct");
        when(planExecutor.execute(initial, 1L))
                .thenReturn(List.of(result("hybridSearchProduct", "SUCCESS", "{\"empty\":true}")));
        when(plannerService.plan(any(String.class), eq(skill))).thenReturn(retry);
        when(planExecutor.execute(retry, 1L))
                .thenReturn(List.of(result("searchProduct", "SUCCESS", "[{\"id\":1}]")));

        List<ToolResult> results = executor.execute(initial, "手机", skill, 1L);

        assertThat(results).hasSize(2);
        verify(plannerService, times(1)).plan(any(String.class), eq(skill));
    }

    @Test
    void execute_达到最大轮数_停止重规划() {
        AgentPlan initial = plan("hybridSearchProduct");
        AgentPlan retry = plan("searchProduct");
        when(planExecutor.execute(initial, 1L)).thenReturn(List.of());
        when(plannerService.plan(any(String.class), eq(skill))).thenReturn(retry);
        when(planExecutor.execute(retry, 1L)).thenReturn(List.of());

        List<ToolResult> results = executor.execute(initial, "手机", skill, 1L);

        assertThat(results).isEmpty();
        verify(plannerService, times(1)).plan(any(String.class), eq(skill));
        // initial + retry 共执行 2 次
        verify(planExecutor, times(1)).execute(initial, 1L);
        verify(planExecutor, times(1)).execute(retry, 1L);
    }
}
