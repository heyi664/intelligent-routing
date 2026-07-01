package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.routing.RouteDecision;

public interface AgentRuntime {
    ChatResponse execute(ChatRequest request, RouteDecision decision);
}
