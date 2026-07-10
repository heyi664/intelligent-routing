package com.xinchan.voiceqa.ai;

public record ChatModelRequest(
    String systemPrompt,
    String userPrompt,
    String conversationId,
    String traceId,
    ChatModelPurpose purpose
) {
    public ChatModelRequest(String systemPrompt, String userPrompt, String conversationId, String traceId) {
        this(systemPrompt, userPrompt, conversationId, traceId, ChatModelPurpose.AGENT);
    }
}
