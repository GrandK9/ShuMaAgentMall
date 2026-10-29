package com.shumamall.agent.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/**
 * 历史消息展示文本：兼容旧数据中误写入 Redis 的规划回填 prompt。
 */
public final class ChatHistoryText {

    private static final String TOOL_CONTEXT_MARKER = "以下是工具执行结果";
    private static final String USER_MSG_PREFIX = "用户消息：";

    private ChatHistoryText() {
    }

    public static String forDisplay(Message message) {
        if (message == null) {
            return "";
        }
        String text = message.getText();
        if (message.getMessageType() != MessageType.USER || text == null) {
            return text == null ? "" : text;
        }
        if (!text.contains(TOOL_CONTEXT_MARKER)) {
            return text;
        }
        int userStart = text.indexOf(USER_MSG_PREFIX);
        if (userStart < 0) {
            return text;
        }
        int contentStart = userStart + USER_MSG_PREFIX.length();
        int markerStart = text.indexOf(TOOL_CONTEXT_MARKER, contentStart);
        if (markerStart > contentStart) {
            return text.substring(contentStart, markerStart).strip();
        }
        return text;
    }
}
