package com.xinchan.voiceqa.routing;


import org.springframework.stereotype.Component;
import com.xinchan.voiceqa.conversation.ConversationState;

@Component
public class AgentSwitchPolicy {

    public RouteDecision decide(ConversationState state, RouteCandidate candidate) {
        if (candidate.confidence() < 0.60) {
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
