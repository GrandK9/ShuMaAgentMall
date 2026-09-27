package com.shumamall.agent.planning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具目录：汇总全部 {@link ToolCallback} 的工具定义，供规划器生成计划。
 * <p>
 * 工具由 {@code @Tool} 注解方法通过 {@link ToolCallbackProvider}
 * （MethodToolCallbackProvider）统一注册，目录在启动时一次性加载，
 * 新增工具只需在 Provider 中追加工具对象，无需改动规划模块。
 */
@Slf4j
@Component
public class ToolCatalog {

    /** 工具名 → 工具回调（执行入口） */
    private final Map<String, ToolCallback> callbacks;

    public ToolCatalog(ToolCallbackProvider toolCallbackProvider) {
        this.callbacks = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), Function.identity()));
        log.info("工具目录加载完成，共 {} 个工具: {}", callbacks.size(), callbacks.keySet());
    }

    /**
     * 按名称解析工具回调（执行器调用入口）。
     *
     * @throws IllegalArgumentException 工具不存在
     */
    public ToolCallback resolve(String toolName) {
        ToolCallback callback = callbacks.get(toolName);
        if (callback == null) {
            throw new IllegalArgumentException("未知工具: " + toolName);
        }
        return callback;
    }

    /**
     * 工具定义列表。
     */
    public List<ToolDefinition> definitions() {
        return callbacks.values().stream().map(ToolCallback::getToolDefinition).toList();
    }

    /**
     * 判断工具是否存在（校验计划中的 toolName）。
     */
    public boolean contains(String toolName) {
        return callbacks.containsKey(toolName);
    }

    /**
     * 生成工具清单提示文本（含参数 JSON Schema），供 LLM 规划时参考。
     */
    public String buildToolingPrompt() {
        return definitions().stream()
                .map(def -> "- " + def.name() + "\n  描述: " + def.description()
                        + "\n  参数Schema: " + def.inputSchema())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 全部工具名。
     */
    public Set<String> toolNames() {
        return callbacks.keySet();
    }
}
