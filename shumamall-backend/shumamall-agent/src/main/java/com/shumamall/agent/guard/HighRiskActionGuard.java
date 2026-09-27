package com.shumamall.agent.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.planning.AgentPlan;
import com.shumamall.agent.planning.PlanStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 高风险操作拦截器（Human-in-the-Loop）。
 * <p>
 * 对 Agent 计划中的高风险步骤（如下单、加购、取消订单）进行识别：
 * 调用方必须持有一枚由服务端签发、与"当前会话 + 当前步骤 + 具体操作内容"绑定的
 * 一次性确认凭据（见 {@link ConfirmTokenService}），否则拦截并返回新的待确认凭据，
 * 由前端引导用户二次确认后再继续。
 * <p>
 * 安全约束：确认判定完全基于服务端签发的凭据，<b>不信任调用方声明的 stepId</b>——
 * stepId 由规划器生成且可预测，若直接采信，调用方只要在请求里填入 stepId 即可绕过确认。
 */
@Slf4j
@Component
public class HighRiskActionGuard {

    /** 待确认计划缓存前缀，键为会话 ID，与确认凭据同有效期 */
    private static final String PENDING_PLAN_KEY_PREFIX = "agent:pending-plan:";

    private final Set<String> highRiskTools;

    private final ConfirmTokenService confirmTokenService;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public HighRiskActionGuard(@Value("${shumamall.agent.guard.high-risk-tools:createOrder,addToCart,cancelOrder}") String tools,
                               ConfirmTokenService confirmTokenService,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.highRiskTools = Arrays.stream(StringUtils.commaDelimitedListToStringArray(tools))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        this.confirmTokenService = confirmTokenService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 检查计划是否需要用户二次确认。
     *
     * @param plan          当前执行计划
     * @param confirmTokens 前端回传的确认凭据（首次请求为空）
     * @param sessionId     当前会话 ID（凭据与会话绑定）
     * @return 检查结果；被拦截时待确认步骤上会附带新签发的一次性凭据
     */
    public GuardResult check(AgentPlan plan, Set<String> confirmTokens, String sessionId) {
        if (plan == null || CollectionUtils.isEmpty(plan.getSteps())) {
            return GuardResult.pass();
        }
        List<PlanStep> pendingSteps = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            if (!highRiskTools.contains(step.getToolName())) {
                continue;
            }
            if (isConfirmed(sessionId, step, confirmTokens)) {
                continue;
            }
            // 未确认：签发新凭据随确认话术下发（原凭据若已失效/被篡改，此处自然重建）
            step.setConfirmToken(confirmTokenService.issue(sessionId, step));
            pendingSteps.add(step);
        }
        if (pendingSteps.isEmpty()) {
            return GuardResult.pass();
        }
        log.info("高风险操作待确认: sessionId={}, steps={}", sessionId,
                pendingSteps.stream().map(PlanStep::getStepId).toList());
        return GuardResult.block(pendingSteps, buildConfirmMessage(pendingSteps));
    }

    /**
     * 判定单个高风险步骤是否已被有效确认：任一凭据校验并消费通过即视为已确认。
     */
    private boolean isConfirmed(String sessionId, PlanStep step, Set<String> confirmTokens) {
        if (CollectionUtils.isEmpty(confirmTokens)) {
            return false;
        }
        for (String token : confirmTokens) {
            if (confirmTokenService.verifyAndConsume(sessionId, step, token)) {
                return true;
            }
        }
        return false;
    }

    private String buildConfirmMessage(List<PlanStep> riskySteps) {
        String tools = riskySteps.stream()
                .map(PlanStep::getDescription)
                .collect(Collectors.joining("、"));
        return "检测到高风险操作：%s。请确认是否继续？".formatted(tools);
    }

    /**
     * 记住「被拦截、等待用户确认的那份计划」。
     * <p>
     * <b>为什么需要记住计划</b>：确认凭据绑定的是 {@code sessionId + stepId + 工具 + 参数摘要}
     * （见 {@link ConfirmTokenService}），而每一次请求都会重新跑一遍 LLM 规划。用户点「确认执行」后
     * 前端重发的是同一条消息，但重新规划的结果未必逐字相同 —— 实测模型有时会多吐
     * {@code productId / skuId / addressId} 这类取值为 {@code null} 或 {@code 0} 的占位参数，
     * 参数摘要随即不匹配、凭据被判无效，用户看到「明明确认了却又要再确认一次」。
     * <p>
     * 解法：拦截时把这份计划按会话缓存，用户确认后直接执行缓存里的计划，不再重新规划。
     * 语义上也更正确 —— 执行的就是用户看到并确认过的那一份操作。
     *
     * @param sessionId 会话 ID
     * @param message   触发本次计划的用户消息（复用时必须比对该消息）
     * @param plan      被拦截的计划
     */
    public void rememberPendingPlan(String sessionId, String message, AgentPlan plan) {
        try {
            redisTemplate.opsForValue().set(PENDING_PLAN_KEY_PREFIX + sessionId,
                    objectMapper.writeValueAsString(new PendingPlan(message, plan)),
                    Duration.ofSeconds(ConfirmTokenService.TTL_SECONDS));
        } catch (Exception e) {
            // 缓存失败不阻断主流程：退化为「重新规划」，最坏情况是多弹一次确认框
            log.warn("缓存待确认计划失败: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    /**
     * 取出被拦截计划的缓存，仅在「同一条消息」重发时复用，否则返回 {@code null} 由调用方重新规划。
     * <p>
     * <b>为什么必须比对消息</b>：前端的 confirmTokens 在会话内是累积回传的，若只凭「带了凭据」
     * 就复用缓存，用户确认下单之后随便再说一句别的，就会被套用上一轮那份下单计划。
     *
     * @return 可复用的计划；无缓存 / 消息不符 / 解析失败时返回 {@code null}
     */
    public AgentPlan loadPendingPlan(String sessionId, String message) {
        try {
            String raw = redisTemplate.opsForValue().get(PENDING_PLAN_KEY_PREFIX + sessionId);
            if (raw == null) {
                return null;
            }
            PendingPlan pending = objectMapper.readValue(raw, PendingPlan.class);
            AgentPlan plan = pending.plan();
            if (!Objects.equals(pending.message(), message)) {
                log.info("待确认计划与本次消息不一致，改为重新规划: sessionId={}", sessionId);
                return null;
            }
            if (plan == null || CollectionUtils.isEmpty(plan.getSteps())) {
                return null;
            }
            // 缓存里的凭据是上一次签发的，执行链路本就不读它，回填给前端只会造成误解
            plan.getSteps().forEach(step -> step.setConfirmToken(null));
            return plan;
        } catch (Exception e) {
            log.warn("读取待确认计划失败，改为重新规划: sessionId={}, err={}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 计划已放行执行，清掉缓存（一份计划只复用一次）。
     */
    public void forgetPendingPlan(String sessionId) {
        redisTemplate.delete(PENDING_PLAN_KEY_PREFIX + sessionId);
    }

    /**
     * 缓存载荷：计划 + 它对应的用户消息。
     */
    public record PendingPlan(String message, AgentPlan plan) {
    }

    /**
     * 返回全部高风险工具名（供配置校验 / 前端展示）。
     */
    public Set<String> highRiskTools() {
        return Collections.unmodifiableSet(new HashSet<>(highRiskTools));
    }
}
