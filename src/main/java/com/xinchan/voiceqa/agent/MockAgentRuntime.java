package com.xinchan.voiceqa.agent;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockAgentRuntime implements AgentRuntime {
    private final ConversationStateRepository stateRepository;
    private final Map<RouteTarget, ChatAgent> agents;
    private int executionCount;

    @Autowired
    public MockAgentRuntime(ConversationStateRepository stateRepository, List<ChatAgent> agents) {
        this.stateRepository = stateRepository;
        this.agents = new HashMap<>();
        for (ChatAgent agent : agents) {
            this.agents.put(agent.target(), agent);
        }
    }


    @Override
    public ChatResponse execute(ChatRequest request, RouteDecision decision) {
        executionCount++;
        stateRepository.saveCurrentAgent(request.conversationId(), decision.target());

        ChatAgent agent = agents.getOrDefault(decision.target(), agents.get(RouteTarget.FALLBACK_AGENT));
        if (agent == null) {
            throw new IllegalStateException("No agent registered for target " + decision.target());
        }
        return new ChatResponse(
            request.conversationId(),
            agent.target(),
            agent.answer(request, decision),
            agent.target().name()
        );
    }

    public int executionCount() {
        return executionCount;
    }
}
