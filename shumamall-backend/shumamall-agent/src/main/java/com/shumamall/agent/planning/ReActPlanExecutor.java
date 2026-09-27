package com.shumamall.agent.planning;

import com.shumamall.agent.dto.ToolResult;
import com.shumamall.agent.skill.AgentSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 多轮工具调用执行器。
 * <p>
 * 在 {@link PlanExecutor} 基础上增加"观察 → 思考 → 再行动"循环：
 * <ol>
 *   <li>执行初始计划，收集工具结果</li>
 *   <li>若结果为空或全部失败，自动构造反思提示词，调用 {@link PlannerService} 生成新计划</li>
 *   <li>最多执行 {@code maxRounds} 轮，最终返回所有轮次的工具结果</li>
 * </ol>
 * 适用于导购场景中首次检索无结果时，自动调整关键词 / 价格区间重新搜索。
 */
@Slf4j
@Service
public class ReActPlanExecutor {

    private final PlannerService plannerService;
    private final PlanExecutor planExecutor;
    private final int maxRounds;

    public ReActPlanExecutor(PlannerService plannerService,
                             PlanExecutor planExecutor,
                             @Value("${shumamall.agent.react.max-rounds:2}") int maxRounds) {
        this.plannerService = plannerService;
        this.planExecutor = planExecutor;
        this.maxRounds = Math.max(1, maxRounds);
    }

    /**
     * 执行计划并在必要时自动重规划。
     *
     * @param initialPlan 初始计划
     * @param userMessage 用户原始消息
     * @param skill       当前技能
     * @param userId      当前用户
     * @return 所有轮次的工具执行结果（按执行顺序）
     */
    public List<ToolResult> execute(AgentPlan initialPlan, String userMessage, AgentSkill skill, Long userId) {
        List<ToolResult> allResults = new ArrayList<>();
        AgentPlan currentPlan = initialPlan;
        String currentMessage = userMessage;

        for (int round = 1; round <= maxRounds; round++) {
            List<ToolResult> roundResults = planExecutor.execute(currentPlan, userId);
            allResults.addAll(roundResults);

            if (!needRethink(roundResults)) {
                log.debug("ReAct 提前结束: round={}", round);
                break;
            }
            if (round >= maxRounds) {
                log.info("ReAct 达到最大轮数: maxRounds={}", maxRounds);
                break;
            }

            // 构造反思提示词，引导规划器调整策略
            currentMessage = buildRethinkMessage(userMessage, roundResults);
            currentPlan = plannerService.plan(currentMessage, skill);
            log.info("ReAct 重新规划: round={}, planSteps={}", round + 1,
                    currentPlan.getSteps() == null ? 0 : currentPlan.getSteps().size());
        }
        return allResults;
    }

    /**
     * 是否需要重新规划：结果为空或全部失败/含 error 标记。
     */
    private boolean needRethink(List<ToolResult> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        return results.stream().allMatch(r -> {
            if ("ERROR".equals(r.getStatus())) {
                return true;
            }
            String result = r.getResult();
            return result == null
                    || result.contains("\"error\"")
                    || result.contains("\"empty\":true");
        });
    }

    private String buildRethinkMessage(String userMessage, List<ToolResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户原请求：").append(userMessage).append("\n\n");
        sb.append("上一轮工具执行结果为空或失败，请调整检索策略（例如放宽价格区间、更换关键词）：\n");
        for (ToolResult r : results) {
            sb.append("- ").append(r.getToolName()).append(" : ").append(r.getStatus()).append("\n");
        }
        return sb.toString();
    }
}
