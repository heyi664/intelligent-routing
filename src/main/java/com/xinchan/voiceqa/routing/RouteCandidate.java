package com.xinchan.voiceqa.routing;

public record RouteCandidate(
    String rewrittenQuestion,
    String intent,
    RouteTarget targetAgent,
    boolean shouldStayInCurrentAgent,
    double confidence,
    String reason
) {
}
