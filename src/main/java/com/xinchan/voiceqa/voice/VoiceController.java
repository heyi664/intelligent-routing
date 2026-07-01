package com.xinchan.voiceqa.voice;

import com.xinchan.voiceqa.api.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    private final VoicePipelineService voicePipelineService;

    public VoiceController(VoicePipelineService voicePipelineService) {
        this.voicePipelineService = voicePipelineService;
    }

    @PostMapping("/demo")
    public ChatResponse demoVoice(@RequestBody VoiceChatRequest request) {
        // TODO: 后续真实语音客服入口改为 multipart 或 WebSocket 音频流上传。
        return voicePipelineService.handle(request);
    }
}