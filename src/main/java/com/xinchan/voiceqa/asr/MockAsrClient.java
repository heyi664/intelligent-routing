package com.xinchan.voiceqa.asr;

import com.xinchan.voiceqa.voice.VoiceInputEvent;

import java.util.List;

public class MockAsrClient implements AsrClient {

    @Override
    public List<AsrResult> transcribe(VoiceInputEvent event) {
        // TODO: 替换为真实 ASR 后，仍保留 partial/stable 两类转写结果的分离。
        return List.of(
            new AsrResult(event.voiceSessionId(), "天然气", false, 0.60, 100),
            new AsrResult(event.voiceSessionId(), event.demoTranscript(), true, 0.95, 400)
        );
    }
}