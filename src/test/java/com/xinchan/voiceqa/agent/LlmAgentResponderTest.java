package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.ai.StreamingChatModelClient;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.memory.ChatTurn;
import com.xinchan.voiceqa.memory.ConversationMemoryService;
import com.xinchan.voiceqa.memory.InMemoryChatHistoryRepository;
import com.xinchan.voiceqa.memory.MemoryProperties;
import com.xinchan.voiceqa.memory.NoopConversationSummaryService;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class LlmAgentResponderTest {

    @Test
    void clarificationAgentIntroducesItselfThroughLlmSystemPrompt(CapturedOutput output) {
        CapturingChatModelClient client = new CapturingChatModelClient("我是澄清助手，可以帮你判断业务类型。");
        ClarificationAgent agent = new ClarificationAgent(
            new LlmAgentResponder(new SpringAiGateway(client), new AgentPromptFactory())
        );

        String answer = agent.answer(
            new ChatRequest("c-llm-1", "u-1", "你是谁"),
            RouteDecision.to(RouteTarget.CLARIFICATION_AGENT)
        );

        assertEquals("我是澄清助手，可以帮你判断业务类型。", answer);
        assertTrue(client.lastRequest.systemPrompt().contains("澄清助手"));
        assertTrue(client.lastRequest.systemPrompt().contains("先简短介绍自己"));
        assertTrue(client.lastRequest.userPrompt().contains("你是谁"));
        assertEquals("c-llm-1", client.lastRequest.conversationId());
        assertTrue(output.getOut().contains("LLM agent request"));
        assertTrue(output.getOut().contains("CLARIFICATION_AGENT"));
        assertTrue(output.getOut().contains("c-llm-1"));
    }

    @Test
    void usesTemplateFallbackWhenLlmFailsAndLogsReason(CapturedOutput output) {
        ClarificationAgent agent = new ClarificationAgent(
            new LlmAgentResponder(
                new SpringAiGateway(request -> {
                    throw new IllegalStateException("missing key");
                }),
                new AgentPromptFactory()
            )
        );

        String answer = agent.answer(
            new ChatRequest("c-fallback", "u-1", "你是谁"),
            RouteDecision.to(RouteTarget.CLARIFICATION_AGENT)
        );

        assertTrue(answer.contains("澄清助手"));
        assertTrue(answer.contains("你可以问我"));
        assertTrue(output.getOut().contains("LLM agent fallback"));
        assertTrue(output.getOut().contains("CLARIFICATION_AGENT"));
        assertTrue(output.getOut().contains("c-fallback"));
        assertTrue(output.getOut().contains("missing key"));
    }

    @Test
    void includesRecentConversationTurnsInAgentPrompt() {
        CapturingChatModelClient client = new CapturingChatModelClient("assistant answer");
        InMemoryChatHistoryRepository historyRepository = new InMemoryChatHistoryRepository();
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentTurnLimit(2);
        ConversationMemoryService memoryService = new ConversationMemoryService(
            historyRepository,
            new NoopConversationSummaryService(),
            properties
        );
        memoryService.recordTurn(new ChatTurn(null, "c-memory", "u-1", "previous user", "previous assistant", RouteTarget.PAYMENT_AGENT, "PAYMENT_AGENT", Instant.parse("2026-07-08T00:00:00Z")));
        LlmAgentResponder responder = new LlmAgentResponder(
            new SpringAiGateway(client),
            new AgentPromptFactory(),
            memoryService
        );

        responder.answer(
            RouteTarget.PAYMENT_AGENT,
            new ChatRequest("c-memory", "u-1", "current user"),
            RouteDecision.to(RouteTarget.PAYMENT_AGENT),
            "fallback"
        );

        assertTrue(client.lastRequest.userPrompt().contains("conversationMemory:"));
        assertTrue(client.lastRequest.userPrompt().contains("U: previous user"));
        assertTrue(client.lastRequest.userPrompt().contains("A: previous assistant"));
        assertTrue(client.lastRequest.userPrompt().contains("current user"));
    }
    @Test
    void streamsLlmDeltasAndReturnsFullAnswer() {
        java.util.concurrent.atomic.AtomicReference<ChatModelRequest> lastRequest = new java.util.concurrent.atomic.AtomicReference<>();
        StreamingChatModelClient client = new StreamingChatModelClient() {
            @Override
            public String streamAsText(ChatModelRequest request) {
                throw new AssertionError("streaming path should use delta callback");
            }

            @Override
            public String streamAsText(ChatModelRequest request, java.util.function.Consumer<String> deltaConsumer) {
                lastRequest.set(request);
                deltaConsumer.accept("part-1 ");
                deltaConsumer.accept("part-2");
                return "part-1 part-2";
            }
        };
        LlmAgentResponder responder = new LlmAgentResponder(new SpringAiGateway(client), new AgentPromptFactory());
        java.util.List<String> deltas = new java.util.ArrayList<>();

        String answer = responder.answerStreaming(
            RouteTarget.SAFETY_AGENT,
            new ChatRequest("c-stream-agent", "u-1", "current user", "trace-agent-1"),
            RouteDecision.to(RouteTarget.SAFETY_AGENT),
            "fallback",
            deltas::add
        );

        assertEquals("part-1 part-2", answer);
        assertEquals(java.util.List.of("part-1 ", "part-2"), deltas);
        assertEquals("trace-agent-1", lastRequest.get().traceId());
    }
    private static class CapturingChatModelClient implements StreamingChatModelClient {
        private final String answer;
        private ChatModelRequest lastRequest;
        private java.util.List<String> streamParts;

        private CapturingChatModelClient(String answer) {
            this.answer = answer;
        }

        @Override
        public String streamAsText(ChatModelRequest request) {
            this.lastRequest = request;
            return answer;
        }
    }
}