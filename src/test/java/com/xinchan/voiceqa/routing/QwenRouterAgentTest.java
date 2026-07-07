package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.ai.StreamingChatModelClient;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class QwenRouterAgentTest {

    @Test
    void parsesStructuredRouterJson(CapturedOutput output) {
        StreamingChatModelClient model = request -> """
            {"rewrittenQuestion":"燃气泄漏应该怎么处理","intent":"SAFETY_EMERGENCY","targetAgent":"SAFETY_AGENT","shouldStayInCurrentAgent":false,"confidence":0.91,"reason":"用户询问燃气泄漏处理"}
            """;
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-1", "u-1", "家里漏气了怎么办"),
            new ConversationState("c-1", "u-1", RouteTarget.PAYMENT_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.SAFETY_AGENT, candidate.targetAgent());
        assertEquals("SAFETY_EMERGENCY", candidate.intent());
        assertEquals(0.91, candidate.confidence(), 0.001);
        assertTrue(output.getOut().contains("Qwen router request conversationId=c-1"));
        assertTrue(output.getOut().contains("Qwen router raw response conversationId=c-1"));
        assertTrue(output.getOut().contains("Qwen router decision conversationId=c-1 targetAgent=SAFETY_AGENT"));
    }

    @Test
    void parsesPrettyPrintedRouterJson() {
        StreamingChatModelClient model = request -> """
            {
              "rewrittenQuestion": "How should a gas leak at home be handled?",
              "intent": "GAS_LEAK_EMERGENCY_HANDLING",
              "targetAgent": "SAFETY_AGENT",
              "shouldStayInCurrentAgent": false,
              "confidence": 0.98,
              "reason": "The user asks about emergency gas leak handling."
            }
            """;
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-3", "u-1", "gas leak at home"),
            new ConversationState("c-3", "u-1", RouteTarget.CLARIFICATION_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.SAFETY_AGENT, candidate.targetAgent());
        assertEquals("GAS_LEAK_EMERGENCY_HANDLING", candidate.intent());
        assertEquals(0.98, candidate.confidence(), 0.001);
    }

    @Test
    void parsesRouterJsonWrappedInMarkdownFence() {
        StreamingChatModelClient model = request -> """
            ```json
            {
              "rewrittenQuestion": "Which file should I review?",
              "intent": "MISSING_CONTEXT",
              "targetAgent": "CLARIFICATION_AGENT",
              "shouldStayInCurrentAgent": false,
              "confidence": 0.2,
              "reason": "The user did not specify the business question."
            }
            ```
            """;
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-4", "u-1", "please check this"),
            new ConversationState("c-4", "u-1", RouteTarget.RAG_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.CLARIFICATION_AGENT, candidate.targetAgent());
        assertEquals("MISSING_CONTEXT", candidate.intent());
        assertEquals(0.2, candidate.confidence(), 0.001);
    }

    @Test
    void invalidModelOutputRoutesToClarification(CapturedOutput output) {
        StreamingChatModelClient model = request -> "我觉得需要澄清";
        QwenRouterAgent agent = new QwenRouterAgent(model, new RouterPromptFactory());

        RouteCandidate candidate = agent.classify(
            new ChatRequest("c-2", "u-1", "帮我看看这个"),
            new ConversationState("c-2", "u-1", RouteTarget.CLARIFICATION_AGENT, Instant.now())
        );

        assertEquals(RouteTarget.CLARIFICATION_AGENT, candidate.targetAgent());
        assertEquals("ROUTER_OUTPUT_INVALID", candidate.intent());
        assertEquals(0.0, candidate.confidence(), 0.001);
        assertTrue(output.getOut().contains("Qwen router fallback conversationId=c-2 intent=ROUTER_OUTPUT_INVALID"));
    }
}