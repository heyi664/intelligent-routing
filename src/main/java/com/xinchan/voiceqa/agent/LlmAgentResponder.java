package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmAgentResponder {
    private static final Logger log = LoggerFactory.getLogger(LlmAgentResponder.class);

    private final SpringAiGateway aiGateway;
    private final AgentPromptFactory promptFactory;
    private final boolean enabled;

    public LlmAgentResponder(SpringAiGateway aiGateway, AgentPromptFactory promptFactory) {
        this.aiGateway = aiGateway;
        this.promptFactory = promptFactory;
        this.enabled = true;
    }

    private LlmAgentResponder() {
        this.aiGateway = null;
        this.promptFactory = new AgentPromptFactory();
        this.enabled = false;
    }

    public static LlmAgentResponder localOnly() {
        return new LlmAgentResponder();
    }

    public String answer(
        RouteTarget target,
        ChatRequest request,
        RouteDecision decision,
        String fallbackAnswer
    ) {
        if (!enabled) {
            return fallbackAnswer;
        }
        try {
            String answer = aiGateway.streamAsText(new ChatModelRequest(
                promptFactory.systemPrompt(target),
                promptFactory.userPrompt(request, decision),
                request.conversationId(),
                request.conversationId()
            ));
            if (answer == null || answer.isBlank()) {
                log.warn(
                    "LLM agent fallback targetAgent={} conversationId={} reason=empty_response",
                    target,
                    request.conversationId()
                );
                return fallbackAnswer;
            }
            return answer;
        } catch (RuntimeException ex) {
            log.warn(
                "LLM agent fallback targetAgent={} conversationId={} errorType={} errorMessage={}",
                target,
                request.conversationId(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
            );
            return fallbackAnswer;
        }
    }
}