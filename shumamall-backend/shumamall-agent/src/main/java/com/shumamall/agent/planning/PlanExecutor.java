package com.shumamall.agent.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计划执行器（Executor）：顺序执行 {@link AgentPlan} 中的每个步骤。
 * <p>
 * 执行方式：从 {@link ToolCatalog} 按名称解析 {@link ToolCallback}，
 * 将步骤参数序列化为 JSON 后调用回调，并在 {@link ToolContext} 中携带 userId，
 * 工具方法据此代理当前用户身份（回调由 Spring AI 框架自动注入 ToolContext 参数）。
 */
@Slf4j
@Service
public class PlanExecutor {

    private final ToolCatalog toolCatalog;
    private final ObjectMapper objectMapper;

    public PlanExecutor(ToolCatalog toolCatalog, ObjectMapper objectMapper) {
        this.toolCatalog = toolCatalog;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行计划，返回每个步骤的工具调用结果。
     *
     * @param plan   执行计划
     * @param userId 当前用户 ID（注入 ToolContext，工具内部据此识别身份）
     */
    public List<ToolResult> execute(AgentPlan plan, Long userId) {
        List<ToolResult> results = new ArrayList<>();
        if (plan == null || plan.getSteps() == null) {
            return results;
        }
        ToolContext toolContext = new ToolContext(Map.of("userId", userId));
        for (PlanStep step : plan.getSteps()) {
            results.add(executeStep(step, toolContext));
        }
        return results;
    }

    private ToolResult executeStep(PlanStep step, ToolContext toolContext) {
        ToolResult.ToolResultBuilder builder = ToolResult.builder()
                .toolName(step.getToolName())
                .args(safeToJson(step.getParams()));
        try {
            ToolCallback callback = toolCatalog.resolve(step.getToolName());
            String output = callback.call(safeToJson(step.getParams()), toolContext);
            // 工具内部会把下游失败 catch 成 {"error":"..."} 并正常返回（见 BaseTool#error），
            // 只按「有没有抛异常」判定会把失败记成"工具执行成功"——日志与前端卡片都会说谎，
            // 排查时看不到真实原因。这里识别约定的失败形状，如实记为 ERROR。
            if (isToolErrorOutput(output)) {
                log.warn("工具返回业务错误: tool={}, output={}", step.getToolName(), truncate(output));
                return builder.status("ERROR").result(output).build();
            }
            log.info("工具执行成功: tool={}, output={}", step.getToolName(), truncate(output));
            return builder.status("SUCCESS").result(output).build();
        } catch (Exception e) {
            log.error("工具执行失败: tool={}, err={}", step.getToolName(), e.getMessage());
            return builder.status("ERROR").result(e.getMessage()).build();
        }
    }

    /**
     * 是否为工具约定的失败返回。
     * <p>
     * {@code BaseTool#error(...)} 产出的固定形状是 {@code {"error":"..."}}；
     * 正常业务数据都是数组或对象，不会以该键开头。
     */
    private boolean isToolErrorOutput(String output) {
        return output != null && output.startsWith("{\"error\"");
    }

    private String safeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
