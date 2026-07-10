package com.xinchan.voiceqa.asr;

public record StreamingAsrResult(
    String transcript,
    boolean stable,
    int index,
    long startTimeMs,
    long endTimeMs
) {
}
