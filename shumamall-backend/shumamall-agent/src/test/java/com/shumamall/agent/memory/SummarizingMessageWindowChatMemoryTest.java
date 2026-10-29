package com.shumamall.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummarizingMessageWindowChatMemoryTest {

    private final Map<String, MemoryEnvelope> store = new HashMap<>();
    private RedisChatMemoryRepository repository;
    private ConversationSummaryService summaryService;
    private SummarizingMessageWindowChatMemory memory;

    @BeforeEach
    void setUp() {
        repository = mock(RedisChatMemoryRepository.class);
        when(repository.loadEnvelope(anyString()))
                .thenAnswer(inv -> store.getOrDefault(inv.getArgument(0), MemoryEnvelope.empty()));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(repository).saveEnvelope(anyString(), any());
        summaryService = mock(ConversationSummaryService.class);
        when(summaryService.mergeSummary(anyString(), anyList())).thenReturn("合并摘要");
        memory = new SummarizingMessageWindowChatMemory(
                repository, summaryService, 20, 6, 8, true);
    }

    @Test
    void add_whenRoundCountExceedsThreshold_compressesOncePerAdd() {
        String sessionId = "s1";
        for (int i = 0; i < 7; i++) {
            memory.add(sessionId, List.of(
                    new UserMessage("u" + i),
                    new AssistantMessage("a" + i)));
        }
        MemoryEnvelope stored = store.get(sessionId);
        assertEquals(8, stored.getMessages().size());
        assertEquals("合并摘要", stored.getSummary());
        assertEquals(4, stored.getRoundCount());
        verify(summaryService, times(1)).mergeSummary(anyString(), anyList());
    }

    @Test
    void get_includesSummaryAsSystemMessage() {
        String sessionId = "s2";
        store.put(sessionId, new MemoryEnvelope("早先摘要", 2,
                List.of(new UserMessage("hi"), new AssistantMessage("hello"))));
        List<Message> view = memory.get(sessionId);
        assertEquals(3, view.size());
        assertInstanceOf(SystemMessage.class, view.get(0));
        assertTrue(view.get(0).getText().contains("早先摘要"));
    }
}
