package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.routing.RouteDecision;

import java.util.function.Consumer;

public interface AgentRuntime {
    ChatResponse execute(ChatRequest request, RouteDecision decision);

    default ChatResponse executeStreaming(ChatRequest request, RouteDecision decision, Consumer<String> deltaConsumer) {
        ChatResponse response = execute(request, decision);
        if (response.answer() != null && !response.answer().isBlank()) {
            deltaConsumer.accept(response.answer());
        }
        return response;
    }
}