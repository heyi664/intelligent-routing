package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSwitchPolicyTest {

    @Test
    void routesLowConfidenceToClarificationUsingConfiguredThreshold() {
        ChatProperties properties = new ChatProperties();
        properties.setRouteConfidenceThreshold(0.75);
        AgentSwitchPolicy policy = new AgentSwitchPolicy(properties);

        RouteDecision decision = policy.decide(
            new ConversationState("c-1", "u-1", RouteTarget.PAYMENT_AGENT, Instant.now()),
            new RouteCandidate("问题", "PAYMENT", RouteTarget.PAYMENT_AGENT, true, 0.70, "below threshold")
        );

        assertEquals(RouteTarget.CLARIFICATION_AGENT, decision.target());
    }
}
