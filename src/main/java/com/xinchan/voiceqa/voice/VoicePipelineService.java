package com.xinchan.voiceqa.voice;


import org.springframework.stereotype.Service;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.asr.AsrClient;
import com.xinchan.voiceqa.asr.AsrResult;
import com.xinchan.voiceqa.routing.RouterService;

@Service
public class VoicePipelineService {
    private final AsrClient asrClient;
    private final RouterService routerService;

    public VoicePipelineService(AsrClient asrClient, RouterService routerService) {
        this.asrClient = asrClient;
        this.routerService = routerService;
    }

    public ChatResponse handle(VoiceChatRequest request) {
        // ASR partial 只给前端实时展示，只有 stable 文本才进入路由和会话上下文。
        AsrResult stableTranscript = asrClient.transcribe(request.toVoiceInputEvent())
            .stream()
            .filter(AsrResult::stable)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No stable ASR transcript available"));

        // TODO: record ASR partial latency, stable latency, confidence, and voiceSessionId in MetricsRecorder.
        return routerService.route(new ChatRequest(
            request.conversationId(),
            request.userId(),
            stableTranscript.transcript()
        ));
    }
}

