package com.xinchan.voiceqa.api;

public record ChatRequest(
    String conversationId,
    String userId,
    String message
) {
}
