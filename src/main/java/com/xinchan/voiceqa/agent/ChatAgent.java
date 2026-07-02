package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;

public interface ChatAgent {
    RouteTarget target();

    String answer(ChatRequest request, RouteDecision decision);
}