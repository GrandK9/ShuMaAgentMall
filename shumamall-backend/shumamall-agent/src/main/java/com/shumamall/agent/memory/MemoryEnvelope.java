package com.shumamall.agent.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 单会话记忆载体：摘要与原文同 key 存储，{@link #roundCount} 为当前保留的问答轮数。
 */
public final class MemoryEnvelope {

    private String summary;
    private int roundCount;
    private final List<Message> messages;

    public MemoryEnvelope(String summary, int roundCount, List<Message> messages) {
        this.summary = summary == null ? "" : summary;
        this.roundCount = Math.max(0, roundCount);
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public static MemoryEnvelope empty() {
        return new MemoryEnvelope("", 0, new ArrayList<>());
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    public int getRoundCount() {
        return roundCount;
    }

    public void setRoundCount(int roundCount) {
        this.roundCount = Math.max(0, roundCount);
    }

    public List<Message> getMessages() {
        return messages;
    }

    /** 按消息条数同步轮数（一轮 = 1 USER + 1 ASSISTANT）。 */
    public void syncRoundCountFromMessages() {
        this.roundCount = messages.size() / 2;
    }
}
