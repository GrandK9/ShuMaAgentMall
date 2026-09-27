package com.shumamall.agent.tools;

import com.shumamall.agent.feign.FeignErrors;
import org.springframework.ai.chat.model.ToolContext;

/**
 * 工具基类：提供从 {@link ToolContext} 提取当前用户 ID 的公共逻辑。
 * <p>
 * 每个 {@code @Tool} 方法带 ToolContext 参数时，Spring AI 框架会自动注入；
 * userId 由编排器在创建 ToolContext 时写入，工具据此代理当前用户身份调用后端服务。
 */
public abstract class BaseTool {

    /**
     * 从工具上下文中提取当前用户 ID。
     *
     * @throws IllegalStateException 上下文缺失或用户未登录
     */
    protected Long userId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("缺少工具上下文");
        }
        Object userId = toolContext.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("工具上下文缺少 userId，请先登录");
        }
        return Long.valueOf(String.valueOf(userId));
    }

    /**
     * 构造统一错误 JSON（供 LLM 汇总时读取原因）。
     * <p>
     * 提示语里可能出现引号（下游异常描述常带 JSON 响应体），必须转义，否则工具结果不是合法 JSON。
     */
    protected String error(String message) {
        String text = (message == null || message.isBlank()) ? "未知错误" : message;
        return "{\"error\":\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"}";
    }

    /**
     * 由异常构造错误 JSON，自动还原下游业务提示。
     * <p>
     * 工具内部走 Feign 调下游，失败时异常已被 CircuitBreaker 包了一层，
     * 直接取 {@code getMessage()} 只会得到 "No fallback available."，
     * 下游真正的提示（如「缺少请求签名头」）会丢失，故统一走 {@link FeignErrors} 解包。
     */
    protected String error(Throwable cause) {
        return error(FeignErrors.message(cause));
    }
}
