package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.agent.AgentRuntime;
import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.ConversationState;
import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.memory.ChatTurn;
import com.xinchan.voiceqa.memory.ConversationMemoryService;
import com.xinchan.voiceqa.memory.InMemoryChatHistoryRepository;
import com.xinchan.voiceqa.memory.MemoryProperties;
import com.xinchan.voiceqa.memory.NoopConversationSummaryService;
import com.xinchan.voiceqa.observability.ObservabilityMetrics;
import com.xinchan.voiceqa.qa.FastQaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class RouterService {
    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final FastQaService fastQaService;
    private final RouterAgent routerAgent;
    private final AgentSwitchPolicy switchPolicy;
    private final ConversationStateRepository stateRepository;
    private final AgentRuntime agentRuntime;
    private final ChatProperties chatProperties;
    private final ConversationMemoryService memoryService;
    private final ObservabilityMetrics metrics;

    public RouterService(
        FastQaService fastQaService,
        RouterAgent routerAgent,
        AgentSwitchPolicy switchPolicy,
        ConversationStateRepository stateRepository,
        AgentRuntime agentRuntime,
        ChatProperties chatProperties
    ) {
        this(
            fastQaService,
            routerAgent,
            switchPolicy,
            stateRepository,
            agentRuntime,
            chatProperties,
            new ConversationMemoryService(
                new InMemoryChatHistoryRepository(),
                new NoopConversationSummaryService(),
                new MemoryProperties()
            ),
            new ObservabilityMetrics()
        );
    }

    public RouterService(
        FastQaService fastQaService,
        RouterAgent routerAgent,
        AgentSwitchPolicy switchPolicy,
        ConversationStateRepository stateRepository,
        AgentRuntime agentRuntime,
        ChatProperties chatProperties,
        ConversationMemoryService memoryService
    ) {
        this(
            fastQaService,
            routerAgent,
            switchPolicy,
            stateRepository,
            agentRuntime,
            chatProperties,
            memoryService,
            new ObservabilityMetrics()
        );
    }

    @Autowired
    public RouterService(
        FastQaService fastQaService,
        RouterAgent routerAgent,
        AgentSwitchPolicy switchPolicy,
        ConversationStateRepository stateRepository,
        AgentRuntime agentRuntime,
        ChatProperties chatProperties,
        ConversationMemoryService memoryService,
        ObservabilityMetrics metrics
    ) {
        this.fastQaService = fastQaService;
        this.routerAgent = routerAgent;
        this.switchPolicy = switchPolicy;
        this.stateRepository = stateRepository;
        this.agentRuntime = agentRuntime;
        this.chatProperties = chatProperties;
        this.memoryService = memoryService;
        this.metrics = metrics;
    }

    public ChatResponse route(ChatRequest request) {
        return routeInternal(request, null);
    }

    public ChatResponse routeStreaming(ChatRequest request, Consumer<String> deltaConsumer) {
        return routeInternal(request, deltaConsumer);
    }

    private ChatResponse routeInternal(ChatRequest request, Consumer<String> deltaConsumer) {
        long startedAt = System.nanoTime();
        String traceId = traceId(request);
        boolean streaming = deltaConsumer != null;
        log.info("Route request traceId={} conversationId={} userId={} streaming={}", traceId, request.conversationId(), request.userId(), streaming);
        Optional<String> fastAnswer = fastQaService.findAnswer(request.message());
        if (fastAnswer.isPresent()) {
            stateRepository.saveCurrentAgent(request.conversationId(), RouteTarget.QA_AGENT);
            if (deltaConsumer != null) {
                deltaConsumer.accept(fastAnswer.get());
            }
            ChatResponse response = new ChatResponse(request.conversationId(), RouteTarget.QA_AGENT, fastAnswer.get(), "QA");
            recordTurn(request, response);
            logComplete(traceId, request, response, startedAt, "QA_FAST_HIT", streaming);
            return response;
        }

        ConversationState state = stateRepository.findOrCreate(request.conversationId(), request.userId());
        if (chatProperties.manualMode()) {
            RouteDecision decision = new RouteDecision(
                chatProperties.getManualAgent(),
                true,
                false,
                1.0,
                request.message(),
                "manual agent configured"
            );
            stateRepository.saveCurrentAgent(request.conversationId(), decision.target());
            ChatResponse response = executeAgent(request, decision, deltaConsumer);
            recordTurn(request, response);
            logComplete(traceId, request, response, startedAt, "MANUAL_AGENT", streaming);
            return response;
        }

        RouteCandidate candidate = routerAgent.classify(request, state);
        RouteDecision decision = switchPolicy.decide(state, candidate);
        stateRepository.saveCurrentAgent(request.conversationId(), decision.target());
        ChatResponse response = executeAgent(request, decision, deltaConsumer);
        recordTurn(request, response);
        logComplete(traceId, request, response, startedAt, "ROUTED_AGENT", streaming);
        return response;
    }

    private ChatResponse executeAgent(ChatRequest request, RouteDecision decision, Consumer<String> deltaConsumer) {
        return deltaConsumer == null
            ? agentRuntime.execute(request, decision)
            : agentRuntime.executeStreaming(request, decision, deltaConsumer);
    }

    private void recordTurn(ChatRequest request, ChatResponse response) {
        memoryService.recordTurn(new ChatTurn(
            null,
            request.conversationId(),
            request.userId(),
            request.message(),
            response.answer(),
            response.targetAgent(),
            response.source(),
            Instant.now()
        ));
    }

    private void logComplete(String traceId, ChatRequest request, ChatResponse response, long startedAt, String routeSource, boolean streaming) {
        long durationMs = elapsedMs(startedAt);
        metrics.recordRoute(streaming, routeSource, durationMs);
        log.info(
            "Route complete traceId={} conversationId={} targetAgent={} source={} routeSource={} durationMs={} answerChars={}",
            traceId,
            request.conversationId(),
            response.targetAgent(),
            response.source(),
            routeSource,
            durationMs,
            response.answer() == null ? 0 : response.answer().length()
        );
    }

    private static String traceId(ChatRequest request) {
        return request.traceId() == null || request.traceId().isBlank() ? request.conversationId() : request.traceId();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}