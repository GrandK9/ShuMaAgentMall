package com.shumamall.agent.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.planning.AgentPlan;
import com.shumamall.agent.planning.PlanStep;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 高风险操作拦截器单测。
 * <p>
 * 确认判定完全依赖服务端签发的一次性凭据，调用方声明的 stepId 不再被采信。
 * 另覆盖「被拦截计划的缓存与复用」：确认后重发必须复用用户确认过的那份计划，
 * 且只在该消息与缓存计划对应时才复用。
 */
class HighRiskActionGuardTest {

    private static final String SESSION_ID = "sess-1";

    private static final String PENDING_PLAN_KEY = "agent:pending-plan:" + SESSION_ID;

    private final ConfirmTokenService confirmTokenService = mock(ConfirmTokenService.class);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HighRiskActionGuard guard =
            new HighRiskActionGuard("createOrder,addToCart,cancelOrder", confirmTokenService,
                    redisTemplate, objectMapper);

    private PlanStep step(String stepId, String toolName, String description) {
        PlanStep step = new PlanStep();
        step.setStepId(stepId);
        step.setToolName(toolName);
        step.setDescription(description);
        return step;
    }

    private AgentPlan plan(PlanStep... steps) {
        AgentPlan plan = new AgentPlan();
        plan.setGoal("test");
        plan.setSteps(List.of(steps));
        return plan;
    }

    /** 让 redisTemplate.opsForValue() 可用，并返回可 stub 的 valueOps */
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }

    @Test
    void check_非高风险计划_直接放行() {
        GuardResult result = guard.check(plan(step("s1", "searchProduct", "搜索商品")), null, SESSION_ID);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getPendingSteps()).isEmpty();
    }

    @Test
    void check_含高风险步骤且未确认_拦截并签发凭据() {
        PlanStep risky = step("s2", "createOrder", "创建订单");
        when(confirmTokenService.issue(SESSION_ID, risky)).thenReturn("tok-issued");

        GuardResult result = guard.check(plan(step("s1", "searchProduct", "搜索商品"), risky), null, SESSION_ID);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getPendingSteps()).hasSize(1);
        assertThat(result.getPendingSteps().get(0).getStepId()).isEqualTo("s2");
        assertThat(result.getPendingSteps().get(0).getConfirmToken()).isEqualTo("tok-issued");
        assertThat(result.getConfirmMessage()).contains("创建订单");
    }

    @Test
    void check_凭据校验通过_放行() {
        PlanStep risky = step("s1", "addToCart", "加入购物车");
        when(confirmTokenService.verifyAndConsume(eq(SESSION_ID), any(PlanStep.class), eq("tok-valid")))
                .thenReturn(true);

        GuardResult result = guard.check(plan(risky), Set.of("tok-valid"), SESSION_ID);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getPendingSteps()).isEmpty();
    }

    @Test
    void check_凭据无效或伪造_拦截() {
        when(confirmTokenService.verifyAndConsume(anyString(), any(PlanStep.class), anyString()))
                .thenReturn(false);

        GuardResult result = guard.check(plan(step("s1", "createOrder", "创建订单")),
                Set.of("tok-forged", "tok-expired"), SESSION_ID);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getPendingSteps()).hasSize(1);
    }

    @Test
    void check_多高风险步骤_只拦截未确认部分() {
        PlanStep cart = step("s1", "addToCart", "加入购物车");
        PlanStep order = step("s2", "createOrder", "创建订单");
        when(confirmTokenService.verifyAndConsume(eq(SESSION_ID), any(PlanStep.class), eq("tok-cart")))
                .thenAnswer(invocation -> "s1".equals(((PlanStep) invocation.getArgument(1)).getStepId()));

        GuardResult result = guard.check(plan(cart, order), Set.of("tok-cart"), SESSION_ID);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getPendingSteps()).hasSize(1);
        assertThat(result.getPendingSteps().get(0).getStepId()).isEqualTo("s2");
    }

    @Test
    void check_空计划_放行() {
        assertThat(guard.check(null, null, SESSION_ID).isPassed()).isTrue();
        assertThat(guard.check(new AgentPlan(), null, SESSION_ID).isPassed()).isTrue();
    }

    @Test
    void rememberPendingPlan_按会话缓存并用与凭据同有效期() throws Exception {
        ValueOperations<String, String> valueOps = valueOps();
        AgentPlan plan = plan(step("s1", "createOrder", "创建订单"));

        guard.rememberPendingPlan(SESSION_ID, "帮我下单 AirPods", plan);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(PENDING_PLAN_KEY), payload.capture(),
                eq(Duration.ofSeconds(ConfirmTokenService.TTL_SECONDS)));
        // 缓存的是整份计划本体，而不是只留一个标记——复用时才能原样执行用户确认过的操作
        assertThat(payload.getValue()).contains("帮我下单 AirPods").contains("createOrder");
    }

    @Test
    void loadPendingPlan_消息一致_返回原计划并清空旧凭据() throws Exception {
        ValueOperations<String, String> valueOps = valueOps();
        PlanStep risky = step("s2", "createOrder", "创建订单");
        risky.setConfirmToken("tok-上一轮签发");
        when(valueOps.get(PENDING_PLAN_KEY)).thenReturn(objectMapper.writeValueAsString(
                new HighRiskActionGuard.PendingPlan("帮我下单 AirPods",
                        plan(step("s1", "searchProduct", "搜索商品"), risky))));

        AgentPlan loaded = guard.loadPendingPlan(SESSION_ID, "帮我下单 AirPods");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getSteps()).extracting(PlanStep::getToolName)
                .containsExactly("searchProduct", "createOrder");
        assertThat(loaded.getSteps().get(1).getStepId()).isEqualTo("s2");
        // 旧凭据已随上一轮消费/失效，回填给前端只会误导
        assertThat(loaded.getSteps().get(1).getConfirmToken()).isNull();
    }

    @Test
    void loadPendingPlan_消息不一致_不复用() throws Exception {
        ValueOperations<String, String> valueOps = valueOps();
        when(valueOps.get(PENDING_PLAN_KEY)).thenReturn(objectMapper.writeValueAsString(
                new HighRiskActionGuard.PendingPlan("帮我下单 AirPods",
                        plan(step("s1", "createOrder", "创建订单")))));

        assertThat(guard.loadPendingPlan(SESSION_ID, "再推荐几款耳机")).isNull();
    }

    @Test
    void loadPendingPlan_无缓存或内容损坏_不复用() throws Exception {
        ValueOperations<String, String> valueOps = valueOps();

        assertThat(guard.loadPendingPlan(SESSION_ID, "帮我下单 AirPods")).isNull();

        when(valueOps.get(PENDING_PLAN_KEY)).thenReturn("{不是合法 JSON");
        assertThat(guard.loadPendingPlan(SESSION_ID, "帮我下单 AirPods")).isNull();
    }

    @Test
    void rememberPendingPlan_缓存写入失败_不抛出异常() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis 不可用"));

        // 缓存只是优化路径：失败应退化为「重新规划」，不能把整轮对话打断
        guard.rememberPendingPlan(SESSION_ID, "帮我下单", plan(step("s1", "createOrder", "创建订单")));
    }

    @Test
    void forgetPendingPlan_删除会话缓存() {
        guard.forgetPendingPlan(SESSION_ID);

        verify(redisTemplate).delete(PENDING_PLAN_KEY);
    }
}
