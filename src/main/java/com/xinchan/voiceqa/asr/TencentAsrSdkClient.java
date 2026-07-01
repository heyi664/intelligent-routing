package com.xinchan.voiceqa.asr;

import com.xinchan.voiceqa.voice.VoiceInputEvent;

import java.util.List;

public class TencentAsrSdkClient implements AsrClient {
    private final TencentAsrConfig config;
    private final TencentAsrSdkInvoker sdkInvoker;

    public TencentAsrSdkClient(TencentAsrConfig config, TencentAsrSdkInvoker sdkInvoker) {
        this.config = config.validate();
        this.sdkInvoker = sdkInvoker;
    }

    @Override
    public List<AsrResult> transcribe(VoiceInputEvent event) {
        if (event.audioBytes() == null || event.audioBytes().length == 0) {
            throw new IllegalArgumentException("Voice audio bytes are required for Tencent ASR SDK");
        }

        String text = sdkInvoker.recognize(event.audioBytes(), config);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Tencent ASR SDK returned empty transcript");
        }

        // 腾讯云一句话识别/录音识别 SDK 返回的是最终文本，这里统一包装成 stable transcript。
        return List.of(new AsrResult(
            event.voiceSessionId(),
            text,
            true,
            0.95,
            0
        ));
    }
}