package com.xinchan.voiceqa.voice;

public record VoiceChatRequest(
    String voiceSessionId,
    String conversationId,
    String userId,
    byte[] audioBytes
) {
    public VoiceInputEvent toVoiceInputEvent() {
        return new VoiceInputEvent(
            voiceSessionId,
            conversationId,
            userId,
            audioBytes,
            "天然气缴费怎么操作？"
        );
    }
}
