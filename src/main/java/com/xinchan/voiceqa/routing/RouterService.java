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
import com.xinchan.voiceqa.qa.FastQaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RouterService {
    private final FastQaService fastQaService;
    private final RouterAgent routerAgent;
    private final AgentSwitchPolicy switchPolicy;
    private final ConversationStateRepository stateRepository;
    private final AgentRuntime agentRuntime;
    private final ChatProperties chatProperties;
    private final ConversationMemoryService memoryService;

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
            )
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
        ConversationMemoryService memoryService
    ) {
        this.fastQaService = fastQaService;
        this.routerAgent = routerAgent;
        this.switchPolicy = switchPolicy;
        this.stateRepository = stateRepository;
        this.agentRuntime = agentRuntime;
        this.chatProperties = chatProperties;
        this.memoryService = memoryService;
    }

    public ChatResponse route(ChatRequest request) {
        Optional<String> fastAnswer = fastQaService.findAnswer(request.message());
        if (fastAnswer.isPresent()) {
            stateRepository.saveCurrentAgent(request.conversationId(), RouteTarget.QA_AGENT);
            ChatResponse response = new ChatResponse(request.conversationId(), RouteTarget.QA_AGENT, fastAnswer.get(), "QA");
            recordTurn(request, response);
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
            ChatResponse response = agentRuntime.execute(request, decision);
            recordTurn(request, response);
            return response;
        }

        RouteCandidate candidate = routerAgent.classify(request, state);
        RouteDecision decision = switchPolicy.decide(state, candidate);
        stateRepository.saveCurrentAgent(request.conversationId(), decision.target());
        ChatResponse response = agentRuntime.execute(request, decision);
        recordTurn(request, response);
        return response;
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
}
