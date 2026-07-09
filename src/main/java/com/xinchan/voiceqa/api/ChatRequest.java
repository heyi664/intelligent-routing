package com.xinchan.voiceqa.api;

public record ChatRequest(
    String conversationId,
    String userId,
    String message,
    String traceId
) {
    public ChatRequest(String conversationId, String userId, String message) {
        this(conversationId, userId, message, "");
    }
}