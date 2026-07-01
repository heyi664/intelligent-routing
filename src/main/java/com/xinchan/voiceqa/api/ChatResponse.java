package com.xinchan.voiceqa.api;

import com.xinchan.voiceqa.routing.RouteTarget;

public record ChatResponse(
    String conversationId,
    RouteTarget targetAgent,
    String answer,
    String source
) {
}
