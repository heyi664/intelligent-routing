package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeDispatchTest {

    @Test
    void dispatchesToSafetyAgent() {
        MockAgentRuntime runtime = new MockAgentRuntime(new InMemoryConversationStateRepository(), localAgents());

        ChatResponse response = runtime.execute(
            new ChatRequest("c-safety", "u-1", "家里闻到燃气味怎么办？"),
            RouteDecision.to(RouteTarget.SAFETY_AGENT)
        );

        assertEquals(RouteTarget.SAFETY_AGENT, response.targetAgent());
        assertEquals("SAFETY_AGENT", response.source());
        assertTrue(response.answer().contains("阀门"));
    }

    @Test
    void dispatchesToBusinessDecisionAgent() {
        MockAgentRuntime runtime = new MockAgentRuntime(new InMemoryConversationStateRepository(), localAgents());

        ChatResponse response = runtime.execute(
            new ChatRequest("c-business", "u-1", "分析一下明天供气风险"),
            RouteDecision.to(RouteTarget.BUSINESS_DECISION_AGENT)
        );

        assertEquals(RouteTarget.BUSINESS_DECISION_AGENT, response.targetAgent());
        assertEquals("BUSINESS_DECISION_AGENT", response.source());
        assertTrue(response.answer().contains("风险"));
    }


    private LlmAgentResponder localResponder() {
        return new LlmAgentResponder(
            new SpringAiGateway(request -> {
                throw new IllegalStateException("local test responder");
            }),
            new AgentPromptFactory()
        );
    }

    private java.util.List<ChatAgent> localAgents() {
        return java.util.List.of(
            new PaymentAgent(localResponder()),
            new SafetyAgent(localResponder()),
            new BusinessDecisionAgent(localResponder()),
            new RagAgent(localResponder()),
            new ClarificationAgent(localResponder()),
            new FallbackAgent(localResponder())
        );
    }
}
