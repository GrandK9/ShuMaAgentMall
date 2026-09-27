package com.shumamall.agent.planning;

import com.shumamall.agent.skill.AgentSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规划模块（Planner）：将用户消息 + 技能约束 + 工具目录，转换为可执行的工具调用计划。
 * <p>
 * 设计为 Plan-then-Execute：先让 LLM 产出结构化计划（{@link AgentPlan}），
 * 再由 {@link PlanExecutor} 顺序执行。mock-llm 模式下退化为关键词规则生成单步计划。
 */
@Slf4j
@Service
public class PlannerService {

    private final ChatClient chatClient;
    private final ToolCatalog toolCatalog;
    private final boolean mockLlm;
    private final int maxSteps;

    public PlannerService(ChatClient chatClient,
                          ToolCatalog toolCatalog,
                          @Value("${shumamall.agent.mock-llm:false}") boolean mockLlm,
                          @Value("${shumamall.agent.planning.max-steps:5}") int maxSteps) {
        this.chatClient = chatClient;
        this.toolCatalog = toolCatalog;
        this.mockLlm = mockLlm;
        this.maxSteps = maxSteps;
    }

    /**
     * 生成执行计划。
     *
     * @param userMessage 用户消息
     * @param skill       当前命中的技能（提供行为边界）
     * @return 校验通过的计划
     */
    public AgentPlan plan(String userMessage, AgentSkill skill) {
        AgentPlan plan = mockLlm ? ruleBasedPlan(userMessage) : llmPlan(userMessage, skill);
        return validate(plan);
    }

    private AgentPlan llmPlan(String userMessage, AgentSkill skill) {
        BeanOutputConverter<AgentPlan> converter = new BeanOutputConverter<>(AgentPlan.class);
        String prompt = """
                你是任务规划器。根据用户消息与当前技能边界，选择可用工具生成一份顺序执行的计划，只输出 JSON。
                
                当前技能：%s
                可用工具（toolName 必须取自这里，params 的键必须与参数 Schema 一致）：
                %s
                输出格式：
                {"goal": "一句话目标", "steps": [
                  {"stepId": "step-1", "description": "做什么", "toolName": "工具名", "params": {"参数名": 值}},
                  ...
                ]}
                规则：
                - 步骤最多 %d 步，优先用最少步骤完成目标
                - 下单前需要地址时，先规划 getAddressList 查询地址
                - createOrder / addToCart 的 productKeyword 参数必须填用户消息中的商品名称，禁止填 0 或猜测的 ID
                - 工具参数中的数字 ID（productId/skuId/addressId）如果用户没有明确给出，一律省略，工具会自动解析
                - 只返回 JSON，不要任何解释
                %s
                用户消息：%s""".formatted(
                skill.getSkillId(),
                toolCatalog.buildToolingPrompt(),
                maxSteps,
                converter.getFormat(),
                userMessage);
        try {
            String json = chatClient.prompt().user(prompt).call().content();
            AgentPlan plan = converter.convert(json);
            log.info("规划结果: goal={}, steps={}", plan.getGoal(),
                    plan.getSteps() == null ? 0 : plan.getSteps().size());
            return plan;
        } catch (Exception e) {
            log.warn("LLM 规划失败，退回规则规划: err={}", e.getMessage());
            return ruleBasedPlan(userMessage);
        }
    }

    /**
     * mock 模式：按关键词生成单步计划，保证链路可演示。
     */
    private AgentPlan ruleBasedPlan(String userMessage) {
        PlanStep step;
        if (containsAny(userMessage, "购物车", "加购", "放进购物车")) {
            step = step("step-1", "查询当前用户购物车", "getCart", Map.of());
        } else if (containsAny(userMessage, "下单", "购买", "买", "结算", "支付")) {
            step = step("step-1", "查询收货地址列表", "getAddressList", Map.of());
        } else if (containsAny(userMessage, "订单", "发货", "物流")) {
            step = step("step-1", "查询我的订单列表", "listOrders", Map.of());
        } else if (containsAny(userMessage, "地址")) {
            step = step("step-1", "查询收货地址列表", "getAddressList", Map.of());
        } else if (containsAny(userMessage, "个人信息", "我的信息", "账号", "昵称", "手机号")) {
            step = step("step-1", "查询当前用户信息", "getUserInfo", Map.of());
        } else {
            step = step("step-1", "按关键词搜索商品", "searchProduct", Map.of("keyword", userMessage, "limit", 5));
        }
        AgentPlan plan = new AgentPlan();
        plan.setGoal("根据用户消息执行一次工具调用");
        plan.setSteps(List.of(step));
        return plan;
    }

    /**
     * 校验并修正计划：过滤不存在的工具、截断超长步骤。
     */
    private AgentPlan validate(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            log.warn("规划结果为空，生成兜底单步计划");
            return ruleBasedPlan("search");
        }
        List<PlanStep> validSteps = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            if (!toolCatalog.contains(step.getToolName())) {
                log.warn("计划包含未知工具，跳过: toolName={}", step.getToolName());
                continue;
            }
            validSteps.add(step);
            if (validSteps.size() >= maxSteps) {
                break;
            }
        }
        if (validSteps.isEmpty()) {
            log.warn("计划全部步骤无效，生成兜底单步计划");
            return ruleBasedPlan("search");
        }
        plan.setSteps(validSteps);
        return plan;
    }

    private PlanStep step(String id, String description, String toolName, Map<String, Object> params) {
        PlanStep step = new PlanStep();
        step.setStepId(id);
        step.setDescription(description);
        step.setToolName(toolName);
        step.setParams(params);
        return step;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
