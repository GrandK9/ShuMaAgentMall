package com.shumamall.agent.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryTextTest {

    @Test
    void stripsInternalToolContextPrompt() {
        String raw = """
                用户消息：确认全部取消

                以下是工具执行结果，请据此组织回复（无结果时说明原因）：
                [{"toolName":"cancelOrder"}]""";
        assertThat(ChatHistoryText.forDisplay(new UserMessage(raw))).isEqualTo("确认全部取消");
    }

    @Test
    void leavesNormalUserMessageUntouched() {
        assertThat(ChatHistoryText.forDisplay(new UserMessage("全部取消"))).isEqualTo("全部取消");
    }
}
