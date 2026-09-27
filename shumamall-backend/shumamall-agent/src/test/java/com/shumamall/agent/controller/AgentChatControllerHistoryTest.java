package com.shumamall.agent.controller;

import com.shumamall.agent.memory.ConversationService;
import com.shumamall.agent.service.AgentOrchestrator;
import com.shumamall.agent.skill.SkillRegistry;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentChatController /chat/history 接口单元测试。
 */
class AgentChatControllerHistoryTest {

    private final AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
    private final SkillRegistry skillRegistry = mock(SkillRegistry.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentChatController controller = new AgentChatController(
            orchestrator, skillRegistry, conversationService, objectMapper, Executors.newSingleThreadExecutor());

    @BeforeEach
    void setUp() {
        SecurityContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void chatHistory_whenOwner_returnsMessages() {
        // given
        String sessionId = "sess-001";
        List<Message> history = List.of(
                new UserMessage("你好"),
                new AssistantMessage("您好，有什么可以帮您？"),
                new UserMessage("推荐手机")
        );
        when(conversationService.isOwner(sessionId, 1L)).thenReturn(true);
        when(conversationService.findHistory(sessionId)).thenReturn(history);

        // when
        R<List<Map<String, Object>>> resp = controller.chatHistory(sessionId);

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
        assertThat(resp.getData()).hasSize(3);
        assertThat(resp.getData().get(0).get("role")).isEqualTo("user");
        assertThat(resp.getData().get(0).get("content")).isEqualTo("你好");
        assertThat(resp.getData().get(1).get("role")).isEqualTo("assistant");
    }

    @Test
    void chatHistory_whenNotOwner_returnsEmptyList() {
        // given
        String sessionId = "sess-002";
        when(conversationService.isOwner(sessionId, 1L)).thenReturn(false);

        // when
        R<List<Map<String, Object>>> resp = controller.chatHistory(sessionId);

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
        assertThat(resp.getData()).isEmpty();
    }

    @Test
    void chatHistory_whenSessionIdBlank_returnsEmptyList() {
        // when
        R<List<Map<String, Object>>> resp = controller.chatHistory("  ");

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
        assertThat(resp.getData()).isEmpty();
    }

    @Test
    void chatHistory_whenUnauthenticated_returns401() {
        // given
        SecurityContext.clear();

        // when
        R<List<Map<String, Object>>> resp = controller.chatHistory("sess-003");

        // then
        assertThat(resp.getCode()).isEqualTo(4010);
    }
}
