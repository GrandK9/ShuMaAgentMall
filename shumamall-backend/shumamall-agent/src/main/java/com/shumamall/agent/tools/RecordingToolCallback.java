package com.shumamall.agent.tools;

import com.shumamall.agent.dto.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.function.Consumer;

/**
 * 包装 {@link ToolCallback}，在原生 function calling 执行时记下入参与返回，供前端展示。
 */
public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<ToolResult> sink;
    private final Consumer<ToolResult> onRecorded;

    public RecordingToolCallback(ToolCallback delegate, List<ToolResult> sink) {
        this(delegate, sink, null);
    }

    public RecordingToolCallback(ToolCallback delegate, List<ToolResult> sink, Consumer<ToolResult> onRecorded) {
        this.delegate = delegate;
        this.sink = sink;
        this.onRecorded = onRecorded;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return record(toolInput, delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return record(toolInput, delegate.call(toolInput, toolContext));
    }

    private String record(String toolInput, String output) {
        boolean failed = output != null && output.startsWith("{\"error\"");
        ToolResult recorded = ToolResult.builder()
                .toolName(delegate.getToolDefinition().name())
                .args(toolInput)
                .status(failed ? "ERROR" : "SUCCESS")
                .result(output)
                .build();
        if (sink != null) {
            sink.add(recorded);
        }
        if (onRecorded != null) {
            onRecorded.accept(recorded);
        }
        return output;
    }
}
