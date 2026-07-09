package com.xinchan.voiceqa.observability;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ObservabilityMetrics {
    private final AtomicLong routeRequests = new AtomicLong();
    private final AtomicLong routeStreamingRequests = new AtomicLong();
    private final AtomicLong routeFastQaHits = new AtomicLong();
    private final AtomicLong routeAgentCalls = new AtomicLong();
    private final AtomicLong routeLastDurationMs = new AtomicLong();
    private final AtomicLong llmAgentCalls = new AtomicLong();
    private final AtomicLong llmAgentFallbacks = new AtomicLong();
    private final AtomicLong llmAgentLastDurationMs = new AtomicLong();
    private final AtomicLong voiceSessionsStarted = new AtomicLong();
    private final AtomicLong voiceSessionsCompleted = new AtomicLong();
    private final AtomicLong voiceErrors = new AtomicLong();
    private final AtomicLong voiceAudioBytes = new AtomicLong();
    private final AtomicLong asrCalls = new AtomicLong();
    private final AtomicLong asrLastDurationMs = new AtomicLong();
    private final AtomicLong chatStreamDeltas = new AtomicLong();
    private final AtomicLong chatStreamDeltaChars = new AtomicLong();

    public void recordRoute(boolean streaming, String routeSource, long durationMs) {
        routeRequests.incrementAndGet();
        if (streaming) {
            routeStreamingRequests.incrementAndGet();
        }
        if ("QA_FAST_HIT".equals(routeSource)) {
            routeFastQaHits.incrementAndGet();
        } else {
            routeAgentCalls.incrementAndGet();
        }
        routeLastDurationMs.set(Math.max(0, durationMs));
    }

    public void recordLlmAgent(long durationMs, boolean fallback) {
        llmAgentCalls.incrementAndGet();
        if (fallback) {
            llmAgentFallbacks.incrementAndGet();
        }
        llmAgentLastDurationMs.set(Math.max(0, durationMs));
    }

    public void recordVoiceStarted() {
        voiceSessionsStarted.incrementAndGet();
    }

    public void recordVoiceCompleted() {
        voiceSessionsCompleted.incrementAndGet();
    }

    public void recordVoiceError() {
        voiceErrors.incrementAndGet();
    }

    public void recordVoiceAudioBytes(long bytes) {
        if (bytes > 0) {
            voiceAudioBytes.addAndGet(bytes);
        }
    }

    public void recordAsr(long durationMs) {
        asrCalls.incrementAndGet();
        asrLastDurationMs.set(Math.max(0, durationMs));
    }

    public void recordChatStreamDelta(String delta) {
        chatStreamDeltas.incrementAndGet();
        chatStreamDeltaChars.addAndGet(delta == null ? 0 : delta.length());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("route.requests", routeRequests.get());
        values.put("route.streamingRequests", routeStreamingRequests.get());
        values.put("route.fastQaHits", routeFastQaHits.get());
        values.put("route.agentCalls", routeAgentCalls.get());
        values.put("route.lastDurationMs", routeLastDurationMs.get());
        values.put("llm.agentCalls", llmAgentCalls.get());
        values.put("llm.agentFallbacks", llmAgentFallbacks.get());
        values.put("llm.agentLastDurationMs", llmAgentLastDurationMs.get());
        values.put("voice.sessionsStarted", voiceSessionsStarted.get());
        values.put("voice.sessionsCompleted", voiceSessionsCompleted.get());
        values.put("voice.errors", voiceErrors.get());
        values.put("voice.audioBytes", voiceAudioBytes.get());
        values.put("asr.calls", asrCalls.get());
        values.put("asr.lastDurationMs", asrLastDurationMs.get());
        values.put("chat.streamDeltas", chatStreamDeltas.get());
        values.put("chat.streamDeltaChars", chatStreamDeltaChars.get());
        return values;
    }
}