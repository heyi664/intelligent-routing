package com.xinchan.voiceqa.asr;

import com.xinchan.voiceqa.voice.VoiceInputEvent;

import java.util.List;

public interface AsrClient {
    List<AsrResult> transcribe(VoiceInputEvent event);
}
