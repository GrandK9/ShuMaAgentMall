package com.shumamall.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用结果（规划模块执行后的产物，回显给前端 / 拼接给 LLM 汇总）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 工具名称 */
    private String toolName;

    /** 工具入参 */
    private String args;

    /** 执行状态：SUCCESS / ERROR */
    private String status;

    /** 执行结果（工具返回的 JSON 文本） */
    private String result;
}
