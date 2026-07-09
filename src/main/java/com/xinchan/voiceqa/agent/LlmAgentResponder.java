package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.memory.ConversationMemory;
import com.xinchan.voiceqa.memory.ConversationMemoryService;
import com.xinchan.voiceqa.memory.InMemoryChatHistoryRepository;
import com.xinchan.voiceqa.memory.MemoryProperties;
import com.xinchan.voiceqa.memory.NoopConversationSummaryService;
import com.xinchan.voiceqa.observability.ObservabilityMetrics;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class LlmAgentResponder {
    private static final Logger log = LoggerFactory.getLogger(LlmAgentResponder.class);

    private final SpringAiGateway aiGateway;
    private final AgentPromptFactory promptFactory;
    private final ConversationMemoryService memoryService;
    private final ObservabilityMetrics metrics;

    public LlmAgentResponder(SpringAiGateway aiGateway, AgentPromptFactory promptFactory) {
        this(
            aiGateway,
            promptFactory,
            new ConversationMemoryService(
                new InMemoryChatHistoryRepository(),
                new NoopConversationSummaryService(),
                new MemoryProperties()
            ),
            new ObservabilityMetrics()
        );
    }

    public LlmAgentResponder(
        SpringAiGateway aiGateway,
        AgentPromptFactory promptFactory,
        ConversationMemoryService memoryService
    ) {
        this(aiGateway, promptFactory, memoryService, new ObservabilityMetrics());
    }

    @Autowired
    public LlmAgentResponder(
        SpringAiGateway aiGateway,
        AgentPromptFactory promptFactory,
        ConversationMemoryService memoryService,
        ObservabilityMetrics metrics
    ) {
        this.aiGateway = aiGateway;
        this.promptFactory = promptFactory;
        this.memoryService = memoryService;
        this.metrics = metrics;
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
        return answerInternal(target, request, decision, fallbackAnswer, null);
    }

    public String answerStreaming(
        RouteTarget target,
        ChatRequest request,
        RouteDecision decision,
        String fallbackAnswer,
        Consumer<String> deltaConsumer
    ) {
        return answerInternal(target, request, decision, fallbackAnswer, deltaConsumer);
    }

    private String answerInternal(
        RouteTarget target,
        ChatRequest request,
        RouteDecision decision,
        String fallbackAnswer,
        Consumer<String> deltaConsumer
    ) {
        long startedAt = System.nanoTime();
        try {
            log.info(
                "LLM agent request traceId={} targetAgent={} conversationId={}",
                traceId(request),
                target,
                request.conversationId()
            );
            ConversationMemory memory = memoryService.loadForPrompt(request.conversationId());
            ChatModelRequest modelRequest = new ChatModelRequest(
                promptFactory.systemPrompt(target),
                promptFactory.userPrompt(request, decision, memory),
                request.conversationId(),
                traceId(request)
            );
            String answer = deltaConsumer == null
                ? aiGateway.streamAsText(modelRequest)
                : aiGateway.streamAsText(modelRequest, deltaConsumer);
            if (answer == null || answer.isBlank()) {
                metrics.recordLlmAgent(elapsedMs(startedAt), true);
                log.warn(
                    "LLM agent fallback traceId={} targetAgent={} conversationId={} reason=empty_response",
                    traceId(request),
                    target,
                    request.conversationId()
                );
                emitFallback(deltaConsumer, fallbackAnswer);
                return fallbackAnswer;
            }
            metrics.recordLlmAgent(elapsedMs(startedAt), false);
            log.info(
                "LLM agent complete traceId={} targetAgent={} conversationId={} durationMs={} answerChars={}",
                traceId(request),
                target,
                request.conversationId(),
                elapsedMs(startedAt),
                answer.length()
            );
            return answer;
        } catch (RuntimeException ex) {
            metrics.recordLlmAgent(elapsedMs(startedAt), true);
            log.warn(
                "LLM agent fallback traceId={} targetAgent={} conversationId={} durationMs={} errorType={} errorMessage={}",
                traceId(request),
                target,
                request.conversationId(),
                elapsedMs(startedAt),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
            );
            emitFallback(deltaConsumer, fallbackAnswer);
            return fallbackAnswer;
        }
    }

    private static void emitFallback(Consumer<String> deltaConsumer, String fallbackAnswer) {
        if (deltaConsumer != null && fallbackAnswer != null && !fallbackAnswer.isBlank()) {
            deltaConsumer.accept(fallbackAnswer);
        }
    }

    private static String traceId(ChatRequest request) {
        return request.traceId() == null || request.traceId().isBlank() ? request.conversationId() : request.traceId();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}