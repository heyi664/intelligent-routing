package com.xinchan.voiceqa.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.asr.AsrClient;
import com.xinchan.voiceqa.asr.AsrResult;
import com.xinchan.voiceqa.observability.ObservabilityMetrics;
import com.xinchan.voiceqa.routing.RouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class RealtimeVoiceSessionService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeVoiceSessionService.class);
    private final AsrClient asrClient;
    private final RouterService routerService;
    private final ObjectMapper objectMapper;
    private final ObservabilityMetrics metrics;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public RealtimeVoiceSessionService(AsrClient asrClient, RouterService routerService) {
        this(asrClient, routerService, new ObjectMapper(), new ObservabilityMetrics());
    }

    RealtimeVoiceSessionService(AsrClient asrClient, RouterService routerService, ObjectMapper objectMapper) {
        this(asrClient, routerService, objectMapper, new ObservabilityMetrics());
    }

    @Autowired
    RealtimeVoiceSessionService(AsrClient asrClient, RouterService routerService, ObjectMapper objectMapper, ObservabilityMetrics metrics) {
        this.asrClient = asrClient;
        this.routerService = routerService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public void handleText(String sessionId, String payload, Consumer<String> outbound) {
        try {
            JsonNode message = objectMapper.readTree(payload);
            String type = requiredText(message, "type");
            switch (type) {
                case "start" -> start(sessionId, message, outbound);
                case "audio" -> appendAudio(sessionId, message);
                case "end" -> end(sessionId, outbound);
                default -> sendError(outbound, sessionTraceId(sessionId), "Unsupported realtime voice message type: " + type);
            }
        } catch (RuntimeException | IOException ex) {
            metrics.recordVoiceError();
            log.warn("Realtime voice request failed sessionId={} traceId={} errorType={} errorMessage={}", sessionId, sessionTraceId(sessionId), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            sendError(outbound, sessionTraceId(sessionId), ex.getMessage());
        }
    }

    public void close(String sessionId) {
        sessions.remove(sessionId);
    }

    private void start(String sessionId, JsonNode message, Consumer<String> outbound) {
        SessionState state = new SessionState(
            requiredText(message, "voiceSessionId"),
            requiredText(message, "conversationId"),
            requiredText(message, "userId"),
            optionalText(message, "audioFormat", ""),
            optionalInt(message, "sampleRate", 0),
            optionalText(message, "traceId", UUID.randomUUID().toString())
        );
        sessions.put(sessionId, state);
        metrics.recordVoiceStarted();
        log.info("Realtime voice started traceId={} sessionId={} voiceSessionId={} conversationId={} userId={} audioFormat={} sampleRate={}", state.traceId, sessionId, state.voiceSessionId, state.conversationId, state.userId, state.audioFormat, state.sampleRate);
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "started");
        ack.put("traceId", state.traceId);
        ack.put("voiceSessionId", state.voiceSessionId);
        send(outbound, ack);
    }

    private void appendAudio(String sessionId, JsonNode message) {
        SessionState state = requiredSession(sessionId);
        String audioBase64 = requiredText(message, "audioBase64");
        byte[] chunk = Base64.getDecoder().decode(audioBase64);
        if (chunk.length == 0) {
            throw new IllegalArgumentException("audio chunk is empty");
        }
        state.audio.writeBytes(chunk);
        metrics.recordVoiceAudioBytes(chunk.length);
        log.info("Realtime voice audio chunk traceId={} sessionId={} voiceSessionId={} chunkBytes={} totalBytes={}", state.traceId, sessionId, state.voiceSessionId, chunk.length, state.audio.size());
    }

    private void end(String sessionId, Consumer<String> outbound) {
        SessionState state = requiredSession(sessionId);
        long totalStartedAt = System.nanoTime();
        byte[] audioBytes = state.audio.toByteArray();
        log.info("Realtime voice ending traceId={} sessionId={} voiceSessionId={} totalBytes={}", state.traceId, sessionId, state.voiceSessionId, audioBytes.length);
        if (audioBytes.length == 0) {
            throw new IllegalStateException("audio is required before end");
        }

        long asrStartedAt = System.nanoTime();
        AsrResult stableTranscript = asrClient.transcribe(new VoiceInputEvent(
                state.voiceSessionId,
                state.conversationId,
                state.userId,
                audioBytes,
                "",
                state.audioFormat,
                state.sampleRate
            ))
            .stream()
            .filter(AsrResult::stable)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No stable ASR transcript available"));
        long asrDurationMs = elapsedMs(asrStartedAt);
        metrics.recordAsr(asrDurationMs);
        log.info("Realtime voice asr complete traceId={} sessionId={} voiceSessionId={} durationMs={} transcriptChars={} confidence={}", state.traceId, sessionId, state.voiceSessionId, asrDurationMs, stableTranscript.transcript().length(), stableTranscript.confidence());

        sendAsrFinal(outbound, state, stableTranscript);
        sendChatStart(outbound, state);
        long routeStartedAt = System.nanoTime();
        ChatResponse response = routerService.routeStreaming(new ChatRequest(
            state.conversationId,
            state.userId,
            stableTranscript.transcript(),
            state.traceId
        ), delta -> sendChatDelta(outbound, state, delta));
        sendChatDone(outbound, state, response, elapsedMs(routeStartedAt));
        sendChatResponse(outbound, state, response);
        metrics.recordVoiceCompleted();
        log.info("Realtime voice completed traceId={} sessionId={} voiceSessionId={} targetAgent={} totalDurationMs={} routeDurationMs={}", state.traceId, sessionId, state.voiceSessionId, response.targetAgent(), elapsedMs(totalStartedAt), elapsedMs(routeStartedAt));
        sessions.remove(sessionId);
    }

    private SessionState requiredSession(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("voice session has not started");
        }
        return state;
    }

    private String sessionTraceId(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? "" : state.traceId;
    }

    private String optionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText();
    }

    private int optionalInt(JsonNode node, String fieldName, int defaultValue) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        int intValue = value.asInt(defaultValue);
        return intValue > 0 ? intValue : defaultValue;
    }
    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.asText();
    }

    private void sendAsrFinal(Consumer<String> outbound, SessionState state, AsrResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "asr_final");
        node.put("traceId", state.traceId);
        node.put("voiceSessionId", result.voiceSessionId());
        node.put("transcript", result.transcript());
        node.put("confidence", result.confidence());
        node.put("offsetMs", result.offsetMs());
        send(outbound, node);
    }

    private void sendChatStart(Consumer<String> outbound, SessionState state) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_start");
        node.put("traceId", state.traceId);
        node.put("conversationId", state.conversationId);
        send(outbound, node);
    }

    private void sendChatDelta(Consumer<String> outbound, SessionState state, String delta) {
        metrics.recordChatStreamDelta(delta);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_delta");
        node.put("traceId", state.traceId);
        node.put("conversationId", state.conversationId);
        node.put("delta", delta);
        send(outbound, node);
    }

    private void sendChatDone(Consumer<String> outbound, SessionState state, ChatResponse response, long routeDurationMs) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_done");
        node.put("traceId", state.traceId);
        node.put("conversationId", response.conversationId());
        node.put("targetAgent", response.targetAgent().name());
        node.put("source", response.source());
        node.put("routeDurationMs", routeDurationMs);
        send(outbound, node);
    }

    private void sendChatResponse(Consumer<String> outbound, SessionState state, ChatResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_response");
        node.put("traceId", state.traceId);
        node.put("conversationId", response.conversationId());
        node.put("targetAgent", response.targetAgent().name());
        node.put("answer", response.answer());
        node.put("source", response.source());
        send(outbound, node);
    }

    private void sendError(Consumer<String> outbound, String traceId, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "error");
        node.put("traceId", traceId == null ? "" : traceId);
        node.put("message", message == null || message.isBlank() ? "Realtime voice request failed" : message);
        send(outbound, node);
    }

    private void send(Consumer<String> outbound, ObjectNode node) {
        try {
            outbound.accept(objectMapper.writeValueAsString(node));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize realtime voice message", ex);
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static final class SessionState {
        private final String voiceSessionId;
        private final String conversationId;
        private final String userId;
        private final String audioFormat;
        private final int sampleRate;
        private final String traceId;
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();

        private SessionState(String voiceSessionId, String conversationId, String userId, String audioFormat, int sampleRate, String traceId) {
            this.voiceSessionId = voiceSessionId;
            this.conversationId = conversationId;
            this.userId = userId;
            this.audioFormat = audioFormat;
            this.sampleRate = sampleRate;
            this.traceId = traceId;
        }
    }
}