package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.ai.StreamingChatModelClient;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static class CapturingChatModelClient implements StreamingChatModelClient {
        private final String answer;
        private ChatModelRequest lastRequest;

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