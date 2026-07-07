package com.xinchan.voiceqa.routing;


import org.springframework.stereotype.Component;
import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.conversation.ConversationState;

@Component
public class AgentSwitchPolicy {
    private final ChatProperties chatProperties;

    public AgentSwitchPolicy(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    public RouteDecision decide(ConversationState state, RouteCandidate candidate) {
        if (candidate.confidence() < chatProperties.getRouteConfidenceThreshold()) {
            return RouteDecision.to(RouteTarget.CLARIFICATION_AGENT, candidate, false);
        }

        if (candidate.shouldStayInCurrentAgent()) {
            return RouteDecision.to(state.currentAgent(), candidate, true);
        }

        if (candidate.targetAgent() != state.currentAgent()) {
            return RouteDecision.to(candidate.targetAgent(), candidate, false);
        }

        return RouteDecision.to(state.currentAgent(), candidate, true);
    }
}
