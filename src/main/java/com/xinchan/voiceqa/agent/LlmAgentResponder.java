package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.memory.ConversationMemory;
import com.xinchan.voiceqa.memory.ConversationMemoryService;
import com.xinchan.voiceqa.memory.InMemoryChatHistoryRepository;
import com.xinchan.voiceqa.memory.MemoryProperties;
import com.xinchan.voiceqa.memory.NoopConversationSummaryService;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LlmAgentResponder {
    private static final Logger log = LoggerFactory.getLogger(LlmAgentResponder.class);

    private final SpringAiGateway aiGateway;
    private final AgentPromptFactory promptFactory;
    private final ConversationMemoryService memoryService;

    public LlmAgentResponder(SpringAiGateway aiGateway, AgentPromptFactory promptFactory) {
        this(
            aiGateway,
            promptFactory,
            new ConversationMemoryService(
                new InMemoryChatHistoryRepository(),
                new NoopConversationSummaryService(),
                new MemoryProperties()
            )
        );
    }

    @Autowired
    public LlmAgentResponder(
        SpringAiGateway aiGateway,
        AgentPromptFactory promptFactory,
        ConversationMemoryService memoryService
    ) {
        this.aiGateway = aiGateway;
        this.promptFactory = promptFactory;
        this.memoryService = memoryService;
        log.info("LLM agent responder initialized aiGateway={} promptFactory={}",
            aiGateway.getClass().getName(),
            promptFactory.getClass().getName()
        );
    }

    public String answer(
        RouteTarget target,
        ChatRequest request,
        RouteDecision decision,
        String fallbackAnswer
    ) {
        try {
            log.info(
                "LLM agent request targetAgent={} conversationId={}",
                target,
                request.conversationId()
            );
            ConversationMemory memory = memoryService.loadForPrompt(request.conversationId());
            String answer = aiGateway.streamAsText(new ChatModelRequest(
                promptFactory.systemPrompt(target),
                promptFactory.userPrompt(request, decision, memory),
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
            log.info("the answer is {}", answer);
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