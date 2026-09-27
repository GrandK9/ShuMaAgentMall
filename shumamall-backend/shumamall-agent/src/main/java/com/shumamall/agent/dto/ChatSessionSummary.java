package com.shumamall.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话摘要，用于会话列表展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummary {

    /** 会话 ID */
    private String sessionId;

    /** 最后一条消息预览（最多 30 字） */
    private String lastMessagePreview;

    /** 当前会话消息条数 */
    private int messageCount;
}
