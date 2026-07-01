package com.xinchan.voiceqa.routing;


import org.springframework.stereotype.Service;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;

@Service
public class RuleBasedRouterAgent implements RouterAgent {

    @Override
    public RouteCandidate classify(ChatRequest request, ConversationState state) {
        String message = request.message();
        String normalized = message == null ? "" : message.trim();

        // TODO: replace this rule router with a Spring AI structured-output RouterAgent.
        if (containsAny(normalized, "泄漏", "漏气", "阀门", "应急", "报警")) {
            return new RouteCandidate(
                normalized,
                "SAFETY_EMERGENCY",
                RouteTarget.SAFETY_AGENT,
                state.currentAgent() == RouteTarget.SAFETY_AGENT,
                0.92,
                "safety emergency keywords matched"
            );
        }

        if (containsAny(normalized, "缴费", "充值", "账单", "欠费")) {
            return new RouteCandidate(
                normalized,
                "PAYMENT",
                RouteTarget.PAYMENT_AGENT,
                state.currentAgent() == RouteTarget.PAYMENT_AGENT,
                0.88,
                "payment keywords matched"
            );
        }

        if (containsAny(normalized, "分析", "风险", "调度", "供气", "趋势")) {
            return new RouteCandidate(
                normalized,
                "BUSINESS_DECISION",
                RouteTarget.BUSINESS_DECISION_AGENT,
                state.currentAgent() == RouteTarget.BUSINESS_DECISION_AGENT,
                0.80,
                "business decision keywords matched"
            );
        }

        return new RouteCandidate(
            normalized,
            "UNKNOWN",
            RouteTarget.CLARIFICATION_AGENT,
            false,
            0.40,
            "no confident route"
        );
    }

    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
