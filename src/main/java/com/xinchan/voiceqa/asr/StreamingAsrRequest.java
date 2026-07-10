package com.xinchan.voiceqa.asr;

public record StreamingAsrRequest(
    String voiceSessionId,
    String conversationId,
    String userId,
    String audioFormat,
    int sampleRate
) {
}
