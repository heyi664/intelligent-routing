package com.xinchan.voiceqa.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.asr.AsrClient;
import com.xinchan.voiceqa.asr.BufferedStreamingAsrClient;
import com.xinchan.voiceqa.asr.StreamingAsrClient;
import com.xinchan.voiceqa.asr.StreamingAsrListener;
import com.xinchan.voiceqa.asr.StreamingAsrRequest;
import com.xinchan.voiceqa.asr.StreamingAsrResult;
import com.xinchan.voiceqa.asr.StreamingAsrSession;
import com.xinchan.voiceqa.observability.ObservabilityMetrics;
import com.xinchan.voiceqa.routing.RouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
public class RealtimeVoiceSessionService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeVoiceSessionService.class);

    private final StreamingAsrClient streamingAsrClient;
    private final RouterService routerService;
    private final ObjectMapper objectMapper;
    private final ObservabilityMetrics metrics;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public RealtimeVoiceSessionService(AsrClient asrClient, RouterService routerService) {
        this(new BufferedStreamingAsrClient(asrClient), routerService, new ObjectMapper(), new ObservabilityMetrics());
    }

    RealtimeVoiceSessionService(
        AsrClient asrClient,
        RouterService routerService,
        ObjectMapper objectMapper,
        ObservabilityMetrics metrics
    ) {
        this(new BufferedStreamingAsrClient(asrClient), routerService, objectMapper, metrics);
    }

    @Autowired
    RealtimeVoiceSessionService(
        StreamingAsrClient streamingAsrClient,
        RouterService routerService,
        ObjectMapper objectMapper,
        ObservabilityMetrics metrics
    ) {
        this.streamingAsrClient = streamingAsrClient;
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
                case "audio" -> appendAudio(sessionId, decodeAudio(message));
                case "end" -> end(sessionId);
                default -> throw new IllegalArgumentException("Unsupported realtime voice message type: " + type);
            }
        } catch (RuntimeException | IOException ex) {
            handleRequestFailure(sessionId, outbound, ex);
        }
    }

    public void handleBinary(String sessionId, byte[] audioBytes, Consumer<String> outbound) {
        try {
            appendAudio(sessionId, audioBytes);
        } catch (RuntimeException ex) {
            handleRequestFailure(sessionId, outbound, ex);
        }
    }

    public void close(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null && state.terminal.compareAndSet(false, true)) {
            state.closeAsr();
            log.info(
                "Realtime voice connection closed traceId={} sessionId={} voiceSessionId={} totalBytes={}",
                state.traceId,
                sessionId,
                state.voiceSessionId,
                state.totalBytes.get()
            );
        }
    }

    private void start(String sessionId, JsonNode message, Consumer<String> outbound) {
        SessionState previous = sessions.remove(sessionId);
        if (previous != null) {
            previous.closeAsr();
        }

        SessionState state = new SessionState(
            requiredText(message, "voiceSessionId"),
            requiredText(message, "conversationId"),
            requiredText(message, "userId"),
            optionalText(message, "audioFormat", "pcm"),
            optionalInt(message, "sampleRate", 16000),
            optionalText(message, "traceId", UUID.randomUUID().toString()),
            outbound
        );
        sessions.put(sessionId, state);
        try {
            StreamingAsrSession asrSession = streamingAsrClient.start(
                new StreamingAsrRequest(
                    state.voiceSessionId,
                    state.conversationId,
                    state.userId,
                    state.audioFormat,
                    state.sampleRate
                ),
                new StreamingAsrListener() {
                    @Override
                    public void onResult(StreamingAsrResult result) {
                        handleAsrResult(sessionId, state, result);
                    }

                    @Override
                    public void onComplete() {
                        completeRecognition(sessionId, state);
                    }

                    @Override
                    public void onError(Throwable error) {
                        failSession(sessionId, state, error);
                    }
                }
            );
            state.asrSession = asrSession;
            if (state.terminal.get()) {
                asrSession.close();
                return;
            }
        } catch (RuntimeException ex) {
            if (state.terminal.get()) {
                return;
            }
            sessions.remove(sessionId, state);
            state.terminal.set(true);
            throw ex;
        }

        metrics.recordVoiceStarted();
        log.info(
            "Realtime voice started traceId={} sessionId={} voiceSessionId={} conversationId={} userId={} audioFormat={} sampleRate={}",
            state.traceId,
            sessionId,
            state.voiceSessionId,
            state.conversationId,
            state.userId,
            state.audioFormat,
            state.sampleRate
        );
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "started");
        ack.put("traceId", state.traceId);
        ack.put("voiceSessionId", state.voiceSessionId);
        ack.put("audioFormat", state.audioFormat);
        ack.put("sampleRate", state.sampleRate);
        send(outbound, ack);
    }

    private void appendAudio(String sessionId, byte[] chunk) {
        SessionState state = requiredSession(sessionId);
        if (state.ending.get()) {
            throw new IllegalStateException("voice session is already ending");
        }
        if (chunk == null || chunk.length == 0) {
            throw new IllegalArgumentException("audio chunk is empty");
        }
        state.asrSession.sendAudio(chunk);
        long totalBytes = state.totalBytes.addAndGet(chunk.length);
        metrics.recordVoiceAudioBytes(chunk.length);
        log.debug(
            "Realtime voice audio chunk traceId={} sessionId={} voiceSessionId={} chunkBytes={} totalBytes={}",
            state.traceId,
            sessionId,
            state.voiceSessionId,
            chunk.length,
            totalBytes
        );
    }

    private void end(String sessionId) {
        SessionState state = requiredSession(sessionId);
        if (state.totalBytes.get() == 0) {
            throw new IllegalStateException("audio is required before end");
        }
        if (!state.ending.compareAndSet(false, true)) {
            return;
        }
        log.info(
            "Realtime voice ending traceId={} sessionId={} voiceSessionId={} totalBytes={}",
            state.traceId,
            sessionId,
            state.voiceSessionId,
            state.totalBytes.get()
        );
        state.asrSession.stop();
    }

    private void handleAsrResult(String sessionId, SessionState state, StreamingAsrResult result) {
        if (state.terminal.get()) {
            return;
        }
        String transcript = result.transcript() == null ? "" : result.transcript().trim();
        if (result.stable()) {
            if (!transcript.isBlank()) {
                state.finalSegments.put(result.index(), transcript);
            }
            state.partialTranscript = "";
        } else {
            state.partialTranscript = transcript;
        }
        String combined = state.currentTranscript();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "asr_partial");
        node.put("traceId", state.traceId);
        node.put("voiceSessionId", state.voiceSessionId);
        node.put("transcript", combined);
        node.put("stable", result.stable());
        node.put("index", result.index());
        node.put("startTimeMs", result.startTimeMs());
        node.put("endTimeMs", result.endTimeMs());
        send(state.outbound, node);
        log.debug(
            "Realtime voice asr result traceId={} sessionId={} voiceSessionId={} stable={} index={} transcriptChars={}",
            state.traceId,
            sessionId,
            state.voiceSessionId,
            result.stable(),
            result.index(),
            combined.length()
        );
    }

    private void completeRecognition(String sessionId, SessionState state) {
        if (!state.terminal.compareAndSet(false, true)) {
            return;
        }
        String transcript = state.finalTranscript();
        if (transcript.isBlank()) {
            state.terminal.set(false);
            failSession(sessionId, state, new IllegalStateException("No stable ASR transcript available"));
            return;
        }
        long asrDurationMs = elapsedMs(state.startedAt);
        metrics.recordAsr(asrDurationMs);
        sendAsrFinal(state, transcript);
        sendChatStart(state);
        long routeStartedAt = System.nanoTime();
        try {
            ChatResponse response = routerService.routeStreaming(new ChatRequest(
                state.conversationId,
                state.userId,
                transcript,
                state.traceId
            ), delta -> sendChatDelta(state, delta));
            long routeDurationMs = elapsedMs(routeStartedAt);
            sendChatDone(state, response, routeDurationMs);
            sendChatResponse(state, response);
            metrics.recordVoiceCompleted();
            log.info(
                "Realtime voice completed traceId={} sessionId={} voiceSessionId={} targetAgent={} asrDurationMs={} routeDurationMs={} totalBytes={}",
                state.traceId,
                sessionId,
                state.voiceSessionId,
                response.targetAgent(),
                asrDurationMs,
                routeDurationMs,
                state.totalBytes.get()
            );
        } catch (RuntimeException ex) {
            metrics.recordVoiceError();
            sendError(state.outbound, state.traceId, ex.getMessage());
            log.warn(
                "Realtime voice routing failed traceId={} sessionId={} voiceSessionId={} errorType={} errorMessage={}",
                state.traceId,
                sessionId,
                state.voiceSessionId,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
            );
        } finally {
            sessions.remove(sessionId, state);
            state.closeAsr();
        }
    }

    private void failSession(String sessionId, SessionState state, Throwable error) {
        if (!state.terminal.compareAndSet(false, true)) {
            return;
        }
        sessions.remove(sessionId, state);
        state.closeAsr();
        metrics.recordVoiceError();
        sendError(state.outbound, state.traceId, error == null ? null : error.getMessage());
        log.warn(
            "Realtime voice ASR failed traceId={} sessionId={} voiceSessionId={} errorType={} errorMessage={}",
            state.traceId,
            sessionId,
            state.voiceSessionId,
            error == null ? "Unknown" : error.getClass().getSimpleName(),
            error == null ? "" : error.getMessage(),
            error
        );
    }

    private void handleRequestFailure(String sessionId, Consumer<String> outbound, Throwable error) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            failSession(sessionId, state, error);
            return;
        }
        metrics.recordVoiceError();
        sendError(outbound, "", error == null ? null : error.getMessage());
        log.warn(
            "Realtime voice request failed sessionId={} errorType={} errorMessage={}",
            sessionId,
            error == null ? "Unknown" : error.getClass().getSimpleName(),
            error == null ? "" : error.getMessage(),
            error
        );
    }

    private SessionState requiredSession(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("voice session has not started");
        }
        return state;
    }

    private byte[] decodeAudio(JsonNode message) {
        return Base64.getDecoder().decode(requiredText(message, "audioBase64"));
    }

    private String optionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.get(fieldName);
        return value == null || value.asText().isBlank() ? defaultValue : value.asText();
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

    private void sendAsrFinal(SessionState state, String transcript) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "asr_final");
        node.put("traceId", state.traceId);
        node.put("voiceSessionId", state.voiceSessionId);
        node.put("transcript", transcript);
        send(state.outbound, node);
    }

    private void sendChatStart(SessionState state) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_start");
        node.put("traceId", state.traceId);
        node.put("conversationId", state.conversationId);
        send(state.outbound, node);
    }

    private void sendChatDelta(SessionState state, String delta) {
        metrics.recordChatStreamDelta(delta);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_delta");
        node.put("traceId", state.traceId);
        node.put("conversationId", state.conversationId);
        node.put("delta", delta);
        send(state.outbound, node);
    }

    private void sendChatDone(SessionState state, ChatResponse response, long routeDurationMs) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_done");
        node.put("traceId", state.traceId);
        node.put("conversationId", response.conversationId());
        node.put("targetAgent", response.targetAgent().name());
        node.put("source", response.source());
        node.put("routeDurationMs", routeDurationMs);
        send(state.outbound, node);
    }

    private void sendChatResponse(SessionState state, ChatResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat_response");
        node.put("traceId", state.traceId);
        node.put("conversationId", response.conversationId());
        node.put("targetAgent", response.targetAgent().name());
        node.put("answer", response.answer());
        node.put("source", response.source());
        send(state.outbound, node);
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
        private final Consumer<String> outbound;
        private final long startedAt = System.nanoTime();
        private final AtomicLong totalBytes = new AtomicLong();
        private final AtomicBoolean ending = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final ConcurrentSkipListMap<Integer, String> finalSegments = new ConcurrentSkipListMap<>();
        private volatile String partialTranscript = "";
        private volatile StreamingAsrSession asrSession;

        private SessionState(
            String voiceSessionId,
            String conversationId,
            String userId,
            String audioFormat,
            int sampleRate,
            String traceId,
            Consumer<String> outbound
        ) {
            this.voiceSessionId = voiceSessionId;
            this.conversationId = conversationId;
            this.userId = userId;
            this.audioFormat = audioFormat;
            this.sampleRate = sampleRate;
            this.traceId = traceId;
            this.outbound = outbound;
        }

        private String currentTranscript() {
            String stable = finalTranscript();
            if (partialTranscript == null || partialTranscript.isBlank()) {
                return stable;
            }
            return stable.isBlank() ? partialTranscript : stable + partialTranscript;
        }

        private String finalTranscript() {
            return String.join("", finalSegments.values()).trim();
        }

        private void closeAsr() {
            StreamingAsrSession session = asrSession;
            if (session != null) {
                try {
                    session.close();
                } catch (RuntimeException ignored) {
                    // Connection cleanup should not mask the original ASR or routing result.
                }
            }
        }
    }
}
