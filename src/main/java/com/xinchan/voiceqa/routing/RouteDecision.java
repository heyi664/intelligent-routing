package com.xinchan.voiceqa.routing;

public record RouteDecision(
    RouteTarget target,
    boolean stayInCurrentAgent,
    boolean bounceToMainRouter,
    double confidence,
    String rewrittenQuestion,
    String reason
) {
    public static RouteDecision to(RouteTarget target, RouteCandidate candidate, boolean stayInCurrentAgent) {
        return new RouteDecision(
            target,
            stayInCurrentAgent,
            false,
            candidate.confidence(),
            candidate.rewrittenQuestion(),
            candidate.reason()
        );
    }

    public static RouteDecision to(RouteTarget target) {
        return new RouteDecision(target, false, false, 1.0, "", "direct route");
    }
}
