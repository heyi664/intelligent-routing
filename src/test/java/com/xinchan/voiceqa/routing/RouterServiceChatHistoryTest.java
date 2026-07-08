package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.agent.AgentRuntime;
import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository;
import com.xinchan.voiceqa.memory.ConversationMemoryService;
import com.xinchan.voiceqa.memory.InMemoryChatHistoryRepository;
import com.xinchan.voiceqa.memory.MemoryProperties;
import com.xinchan.voiceqa.memory.NoopConversationSummaryService;
import com.xinchan.voiceqa.qa.InMemoryFastQaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterServiceChatHistoryTest {

    @Test
    void recordsWholeTurnForAgentResponse() {
        InMemoryConversationStateRepository stateRepository = new InMemoryConversationStateRepository();
        InMemoryChatHistoryRepository historyRepository = new InMemoryChatHistoryRepository();
        ConversationMemoryService memoryService = memoryService(historyRepository);
        AgentRuntime runtime = (request, decision) -> new ChatResponse(
            request.conversationId(),
            decision.target(),
            "assistant answer",
            decision.target().name()
        );
        RouterService service = new RouterService(
            new InMemoryFastQaService(),
            new RuleBasedRouterAgent(),
            new AgentSwitchPolicy(new ChatProperties()),
            stateRepository,
            runtime,
            ChatProperties.manual(RouteTarget.PAYMENT_AGENT),
            memoryService
        );

        service.route(new ChatRequest("c-history-agent", "u-1", "need account help"));

        var turns = historyRepository.findRecentTurns("c-history-agent", 10);
        assertEquals(1, turns.size());
        assertEquals("need account help", turns.get(0).userMessage());
        assertEquals("assistant answer", turns.get(0).assistantMessage());
        assertEquals(RouteTarget.PAYMENT_AGENT, turns.get(0).targetAgent());
    }

    @Test
    void recordsWholeTurnForFastQaResponse() {
        InMemoryConversationStateRepository stateRepository = new InMemoryConversationStateRepository();
        InMemoryChatHistoryRepository historyRepository = new InMemoryChatHistoryRepository();
        ConversationMemoryService memoryService = memoryService(historyRepository);
        RouterService service = new RouterService(
            new InMemoryFastQaService(),
            new RuleBasedRouterAgent(),
            new AgentSwitchPolicy(new ChatProperties()),
            stateRepository,
            (request, decision) -> { throw new AssertionError("QA should not execute agent runtime"); },
            ChatProperties.manual(RouteTarget.PAYMENT_AGENT),
            memoryService
        );

        service.route(new ChatRequest("c-history-qa", "u-1", "天然气缴费怎么操作"));

        var turns = historyRepository.findRecentTurns("c-history-qa", 10);
        assertEquals(1, turns.size());
        assertEquals("天然气缴费怎么操作", turns.get(0).userMessage());
        assertEquals(RouteTarget.QA_AGENT, turns.get(0).targetAgent());
        assertEquals("QA", turns.get(0).source());
    }

    private ConversationMemoryService memoryService(InMemoryChatHistoryRepository historyRepository) {
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentTurnLimit(8);
        return new ConversationMemoryService(historyRepository, new NoopConversationSummaryService(), properties);
    }
}