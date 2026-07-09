package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;

import java.util.function.Consumer;

public interface ChatAgent {
    RouteTarget target();

    String answer(ChatRequest request, RouteDecision decision);

    default String answerStreaming(ChatRequest request, RouteDecision decision, Consumer<String> deltaConsumer) {
        String answer = answer(request, decision);
        if (answer != null && !answer.isBlank()) {
            deltaConsumer.accept(answer);
        }
        return answer;
    }
}