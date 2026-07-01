package com.xinchan.voiceqa.asr;

import com.xinchan.voiceqa.voice.VoiceInputEvent;

import java.util.List;

public class TencentAsrRealtimeApiClient implements AsrClient {

    @Override
    public List<AsrResult> transcribe(VoiceInputEvent event) {
        // TODO: 后续接腾讯云实时语音识别 WebSocket API。
        // 这一版先用 SDK 做短音频/一句话识别；实时 API 需要处理 partial/stable 流式结果。
        throw new UnsupportedOperationException("Tencent realtime ASR API client is reserved for future work");
    }
}