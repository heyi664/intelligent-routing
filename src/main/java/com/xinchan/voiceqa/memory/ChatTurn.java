package com.xinchan.voiceqa.memory;

import com.xinchan.voiceqa.routing.RouteTarget;

import java.time.Instant;

public record ChatTurn(
    Long id,
    String conversationId,
    String userId,
    String userMessage,
    String assistantMessage,
    RouteTarget targetAgent,
    String source,
    Instant createdAt
) {
}