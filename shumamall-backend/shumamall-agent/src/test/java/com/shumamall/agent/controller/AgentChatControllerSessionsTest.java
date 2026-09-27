package com.shumamall.agent.controller;

import com.shumamall.agent.dto.ChatSessionSummary;
import com.shumamall.agent.memory.ConversationService;
import com.shumamall.agent.service.AgentOrchestrator;
import com.shumamall.agent.skill.SkillRegistry;
import com.shumamall.common.auth.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentChatController 会话列表/删除接口单元测试。
 */
class AgentChatControllerSessionsTest {

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
    void chatSessions_returnsUserSessions() {
        // given
        List<ChatSessionSummary> sessions = List.of(
                ChatSessionSummary.builder()
                        .sessionId("sess-001")
                        .lastMessagePreview("帮我推荐手机")
                        .messageCount(3)
                        .build()
        );
        when(conversationService.findSessions(1L)).thenReturn(sessions);

        // when
        var resp = controller.chatSessions();

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
        assertThat(resp.getData()).hasSize(1);
        assertThat(resp.getData().get(0).getSessionId()).isEqualTo("sess-001");
    }

    @Test
    void chatSessions_whenUnauthenticated_returns401() {
        // given
        SecurityContext.clear();

        // when
        var resp = controller.chatSessions();

        // then
        assertThat(resp.getCode()).isEqualTo(4010);
    }

    @Test
    void deleteChatSession_deletesOwnedSession() {
        // given
        String sessionId = "sess-001";
        when(conversationService.isOwner(sessionId, 1L)).thenReturn(true);

        // when
        var resp = controller.deleteChatSession(sessionId);

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
    }

    @Test
    void deleteChatSession_whenNotOwner_silentlyReturnsOk() {
        // given
        String sessionId = "sess-002";
        when(conversationService.isOwner(sessionId, 1L)).thenReturn(false);

        // when
        var resp = controller.deleteChatSession(sessionId);

        // then
        assertThat(resp.getCode()).isEqualTo(2000);
    }
}
