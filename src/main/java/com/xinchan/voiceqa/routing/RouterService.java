package com.xinchan.voiceqa.routing;


import org.springframework.stereotype.Service;
import com.xinchan.voiceqa.agent.AgentRuntime;
import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.ConversationState;
import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.qa.FastQaService;

import java.util.Optional;

@Service
public class RouterService {
    private final FastQaService fastQaService;
    private final RouterAgent routerAgent;
    private final AgentSwitchPolicy switchPolicy;
    private final ConversationStateRepository stateRepository;
    private final AgentRuntime agentRuntime;
    private final ChatProperties chatProperties;

    public RouterService(
        FastQaService fastQaService,
        RouterAgent routerAgent,
        AgentSwitchPolicy switchPolicy,
        ConversationStateRepository stateRepository,
        AgentRuntime agentRuntime,
        ChatProperties chatProperties
    ) {
        this.fastQaService = fastQaService;
        this.routerAgent = routerAgent;
        this.switchPolicy = switchPolicy;
        this.stateRepository = stateRepository;
        this.agentRuntime = agentRuntime;
        this.chatProperties = chatProperties;
    }

    public ChatResponse route(ChatRequest request) {
        // QA 快速命中必须早于模型和 Agent 调用，用来保护 <=200ms 的首字符目标。
        Optional<String> fastAnswer = fastQaService.findAnswer(request.message());
        if (fastAnswer.isPresent()) {
            stateRepository.saveCurrentAgent(request.conversationId(), RouteTarget.QA_AGENT);
            return new ChatResponse(request.conversationId(), RouteTarget.QA_AGENT, fastAnswer.get(), "QA");
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
            return agentRuntime.execute(request, decision);
        }

        // TODO: replace the rule-based RouterAgent bean with a Qwen structured-output RouterAgent.
        // RouterAgent 只给候选路由，最终跳转由 AgentSwitchPolicy 做确定性决策。
        RouteCandidate candidate = routerAgent.classify(request, state);
        RouteDecision decision = switchPolicy.decide(state, candidate);
        stateRepository.saveCurrentAgent(request.conversationId(), decision.target());
        return agentRuntime.execute(request, decision);
    }
}
