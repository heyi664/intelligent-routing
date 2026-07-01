package com.xinchan.voiceqa.ai;

public record ChatModelRequest(
    String systemPrompt,
    String userPrompt,
    String conversationId,
    String traceId
) {
}
