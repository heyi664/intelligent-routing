package com.xinchan.voiceqa.voice;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.asr.AsrClient;
import com.xinchan.voiceqa.asr.AsrResult;
import com.xinchan.voiceqa.observability.ObservabilityMetrics;
import com.xinchan.voiceqa.routing.RouteTarget;
import com.xinchan.voiceqa.routing.RouterService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeVoiceSessionServiceTest {
    @Test
    void buffersAudioUntilEndThenRunsAsrAndRoutesTranscript() {
        byte[] firstChunk = "hello-".getBytes(StandardCharsets.UTF_8);
        byte[] secondChunk = "voice".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> audioSeenByAsr = new AtomicReference<>();
        AtomicReference<VoiceInputEvent> eventSeenByAsr = new AtomicReference<>();
        AtomicReference<ChatRequest> requestSeenByRouter = new AtomicReference<>();
        List<String> outbound = new ArrayList<>();

        AsrClient asrClient = event -> {
            audioSeenByAsr.set(event.audioBytes());
            eventSeenByAsr.set(event);
            return List.of(new AsrResult(event.voiceSessionId(), "gas leak help", true, 0.96, 120));
        };
        RouterService routerService = new RouterService(null, null, null, null, null, null) {
            @Override
            public ChatResponse route(ChatRequest request) {
                requestSeenByRouter.set(request);
                return new ChatResponse(request.conversationId(), RouteTarget.SAFETY_AGENT, "open windows and call repair", "SAFETY_AGENT");
            }

            @Override
            public ChatResponse routeStreaming(ChatRequest request, java.util.function.Consumer<String> deltaConsumer) {
                requestSeenByRouter.set(request);
                deltaConsumer.accept("open windows and call repair");
                return new ChatResponse(request.conversationId(), RouteTarget.SAFETY_AGENT, "open windows and call repair", "SAFETY_AGENT");
            }
        };
        RealtimeVoiceSessionService service = new RealtimeVoiceSessionService(asrClient, routerService);

        service.handleText("session-1", "{\"type\":\"start\",\"voiceSessionId\":\"v-1\",\"conversationId\":\"c-1\",\"userId\":\"u-1\",\"audioFormat\":\"wav\",\"sampleRate\":16000}", outbound::add);
        service.handleText("session-1", audioMessage(firstChunk), outbound::add);
        service.handleText("session-1", audioMessage(secondChunk), outbound::add);
        service.handleText("session-1", "{\"type\":\"end\"}", outbound::add);

        assertArrayEquals("hello-voice".getBytes(StandardCharsets.UTF_8), audioSeenByAsr.get());
        assertEquals("wav", eventSeenByAsr.get().audioFormat());
        assertEquals(16000, eventSeenByAsr.get().sampleRate());
        assertEquals("c-1", requestSeenByRouter.get().conversationId());
        assertEquals("u-1", requestSeenByRouter.get().userId());
        assertEquals("gas leak help", requestSeenByRouter.get().message());
        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"asr_final\"") && message.contains("gas leak help")));
        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"chat_response\"") && message.contains("SAFETY_AGENT")));
    }

    @Test
    void endWithoutAudioReturnsError() {
        List<String> outbound = new ArrayList<>();
        RealtimeVoiceSessionService service = new RealtimeVoiceSessionService(
            event -> List.of(),
            new RouterService(null, null, null, null, null, null)
        );

        service.handleText("session-2", "{\"type\":\"start\",\"voiceSessionId\":\"v-2\",\"conversationId\":\"c-2\",\"userId\":\"u-1\"}", outbound::add);
        service.handleText("session-2", "{\"type\":\"end\"}", outbound::add);

        Optional<String> error = outbound.stream().filter(message -> message.contains("\"type\":\"error\"")).findFirst();
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("audio"));
    }

    @Test
    void streamsAgentAnswerDeltasAfterAsrFinal() {
        List<String> outbound = new ArrayList<>();
        AsrClient asrClient = event -> List.of(new AsrResult(event.voiceSessionId(), "gas leak help", true, 0.96, 120));
        RouterService routerService = new RouterService(null, null, null, null, null, null) {
            @Override
            public ChatResponse routeStreaming(ChatRequest request, java.util.function.Consumer<String> deltaConsumer) {
                deltaConsumer.accept("open ");
                deltaConsumer.accept("windows");
                return new ChatResponse(request.conversationId(), RouteTarget.SAFETY_AGENT, "open windows", "SAFETY_AGENT");
            }
        };
        ObservabilityMetrics metrics = new ObservabilityMetrics();
        RealtimeVoiceSessionService service = new RealtimeVoiceSessionService(asrClient, routerService, new com.fasterxml.jackson.databind.ObjectMapper(), metrics);

        service.handleText("session-stream", "{\"type\":\"start\",\"voiceSessionId\":\"v-stream\",\"conversationId\":\"c-stream\",\"userId\":\"u-1\",\"traceId\":\"trace-voice-1\"}", outbound::add);
        service.handleText("session-stream", audioMessage("audio".getBytes(StandardCharsets.UTF_8)), outbound::add);
        service.handleText("session-stream", "{\"type\":\"end\"}", outbound::add);

        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"chat_start\"") && message.contains("trace-voice-1")));
        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"chat_delta\"") && message.contains("open ")));
        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"chat_delta\"") && message.contains("windows")));
        assertTrue(outbound.stream().anyMatch(message -> message.contains("\"type\":\"chat_done\"") && message.contains("SAFETY_AGENT")));
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(2L, snapshot.get("chat.streamDeltas"));
        assertEquals(12L, snapshot.get("chat.streamDeltaChars"));
    }
    private String audioMessage(byte[] audioBytes) {
        return "{\"type\":\"audio\",\"audioBase64\":\"" + Base64.getEncoder().encodeToString(audioBytes) + "\"}";
    }
}