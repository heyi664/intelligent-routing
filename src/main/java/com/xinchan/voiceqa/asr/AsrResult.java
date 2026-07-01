package com.xinchan.voiceqa.asr;

public record AsrResult(
    String voiceSessionId,
    String transcript,
    boolean stable,
    double confidence,
    long offsetMs
) {
}
