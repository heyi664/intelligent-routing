package com.xinchan.voiceqa.conversation;

import com.xinchan.voiceqa.routing.RouteTarget;

import java.time.Instant;

public record ConversationState(
    String conversationId,
    String userId,
    RouteTarget currentAgent,
    Instant updatedAt
) {
}
