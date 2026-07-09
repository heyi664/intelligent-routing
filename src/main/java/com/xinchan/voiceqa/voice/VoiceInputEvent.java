package com.xinchan.voiceqa.voice;

public record VoiceInputEvent(
    String voiceSessionId,
    String conversationId,
    String userId,
    byte[] audioBytes,
    String demoTranscript,
    String audioFormat,
    int sampleRate
) {
    public VoiceInputEvent(
        String voiceSessionId,
        String conversationId,
        String userId,
        byte[] audioBytes,
        String demoTranscript
    ) {
        this(voiceSessionId, conversationId, userId, audioBytes, demoTranscript, "", 0);
    }
}