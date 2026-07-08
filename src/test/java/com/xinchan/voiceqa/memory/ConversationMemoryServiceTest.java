package com.xinchan.voiceqa.memory;

import com.xinchan.voiceqa.routing.RouteTarget;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryServiceTest {

    @Test
    void recordsWholeChatTurnsAndReturnsMostRecentTurnsFirstInPromptOrder() {
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentTurnLimit(2);
        InMemoryChatHistoryRepository repository = new InMemoryChatHistoryRepository();
        ConversationMemoryService service = new ConversationMemoryService(repository, new NoopConversationSummaryService(), properties);

        service.recordTurn(new ChatTurn(null, "c-1", "u-1", "first user", "first assistant", RouteTarget.SAFETY_AGENT, "SAFETY_AGENT", Instant.parse("2026-07-08T00:00:00Z")));
        service.recordTurn(new ChatTurn(null, "c-1", "u-1", "second user", "second assistant", RouteTarget.PAYMENT_AGENT, "PAYMENT_AGENT", Instant.parse("2026-07-08T00:01:00Z")));
        service.recordTurn(new ChatTurn(null, "c-1", "u-1", "third user", "third assistant", RouteTarget.RAG_AGENT, "RAG_AGENT", Instant.parse("2026-07-08T00:02:00Z")));

        ConversationMemory memory = service.loadForPrompt("c-1");

        assertEquals(2, memory.recentTurns().size());
        assertEquals("second user", memory.recentTurns().get(0).userMessage());
        assertEquals("third user", memory.recentTurns().get(1).userMessage());
        assertTrue(memory.toPromptBlock().contains("U: second user"));
        assertTrue(memory.toPromptBlock().contains("A: third assistant"));
    }
}