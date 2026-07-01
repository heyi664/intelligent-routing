package com.xinchan.voiceqa.voice;

public record VoiceInputEvent(
    String voiceSessionId,
    String conversationId,
    String userId,
    byte[] audioBytes,
    String demoTranscript
) {
}
