package com.xinchan.voiceqa.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TencentAsrRealtimeApiClient implements StreamingAsrClient {
    private static final Logger log = LoggerFactory.getLogger(TencentAsrRealtimeApiClient.class);
    private static final String REQUEST_PREFIX = "wss://asr.cloud.tencent.com/asr/v2/";
    private static final String SIGN_PREFIX = "asr.cloud.tencent.com/asr/v2/";

    private final TencentRealtimeAsrConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TencentAsrRealtimeApiClient(TencentRealtimeAsrConfig config) {
        this(
            config,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.validate().timeoutMs()))
                .build(),
            new ObjectMapper()
        );
    }

    TencentAsrRealtimeApiClient(TencentRealtimeAsrConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config.validate();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public StreamingAsrSession start(StreamingAsrRequest request, StreamingAsrListener listener) {
        validateRequest(request);
        long timestamp = Instant.now().getEpochSecond();
        int nonce = new Random().nextInt(1_000_000);
        String voiceId = request.voiceSessionId();
        URI uri = buildSignedUri(config, voiceId, timestamp, nonce);
        ProtocolListener protocolListener = new ProtocolListener(voiceId, listener, objectMapper);
        try {
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .buildAsync(uri, protocolListener)
                .get(config.timeoutMs(), TimeUnit.MILLISECONDS);
            protocolListener.awaitReady(config.timeoutMs());
            log.info("Tencent realtime ASR connected voiceSessionId={} engineModelType={}", voiceId, config.engineModelType());
            return new TencentStreamingSession(webSocket, protocolListener, config.timeoutMs(), voiceId);
        } catch (Exception ex) {
            protocolListener.fail(ex);
            throw new IllegalStateException("Failed to start Tencent realtime ASR session", unwrap(ex));
        }
    }

    static URI buildSignedUri(TencentRealtimeAsrConfig config, String voiceId, long timestamp, int nonce) {
        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("convert_num_mode", "1");
        parameters.put("engine_model_type", config.engineModelType());
        parameters.put("expired", Long.toString(timestamp + 86_400));
        parameters.put("filter_dirty", "0");
        parameters.put("filter_empty_result", "1");
        parameters.put("filter_modal", "0");
        parameters.put("filter_punc", "0");
        parameters.put("needvad", "1");
        parameters.put("nonce", Integer.toString(nonce));
        parameters.put("secretid", config.secretId());
        parameters.put("timestamp", Long.toString(timestamp));
        parameters.put("vad_silence_time", Integer.toString(config.vadSilenceTimeMs()));
        parameters.put("voice_format", "1");
        parameters.put("voice_id", voiceId);
        parameters.put("word_info", "0");

        String rawQuery = query(parameters, false);
        String signature = hmacSha1Base64(SIGN_PREFIX + config.appId() + "?" + rawQuery, config.secretKey());
        return URI.create(
            REQUEST_PREFIX + config.appId() + "?" + query(parameters, true)
                + "&signature=" + encode(signature)
        );
    }

    private static String query(TreeMap<String, String> parameters, boolean encoded) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=');
            query.append(encoded ? encode(entry.getValue()) : entry.getValue());
        }
        return query.toString();
    }

    private static String hmacSha1Base64(String source, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Tencent realtime ASR request", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void validateRequest(StreamingAsrRequest request) {
        if (request.voiceSessionId() == null || request.voiceSessionId().isBlank()) {
            throw new IllegalArgumentException("voiceSessionId is required for realtime ASR");
        }
        if (!"pcm".equalsIgnoreCase(request.audioFormat())) {
            throw new IllegalArgumentException("Tencent realtime ASR requires pcm audio");
        }
        if (request.sampleRate() != 16000) {
            throw new IllegalArgumentException("Tencent realtime ASR requires 16000 Hz PCM audio");
        }
    }

    private static Throwable unwrap(Exception error) {
        return error.getCause() == null ? error : error.getCause();
    }

    private static final class TencentStreamingSession implements StreamingAsrSession {
        private final WebSocket webSocket;
        private final ProtocolListener listener;
        private final int timeoutMs;
        private final String voiceId;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private TencentStreamingSession(WebSocket webSocket, ProtocolListener listener, int timeoutMs, String voiceId) {
            this.webSocket = webSocket;
            this.listener = listener;
            this.timeoutMs = timeoutMs;
            this.voiceId = voiceId;
        }

        @Override
        public void sendAudio(byte[] audioBytes) {
            if (stopped.get() || closed.get()) {
                throw new IllegalStateException("Tencent realtime ASR session is not accepting audio");
            }
            if (audioBytes == null || audioBytes.length == 0) {
                throw new IllegalArgumentException("Realtime ASR audio chunk is empty");
            }
            webSocket.sendBinary(ByteBuffer.wrap(audioBytes), true).join();
        }

        @Override
        public void stop() {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            try {
                webSocket.sendText("{\"type\":\"end\"}", true).join();
                listener.awaitComplete(timeoutMs);
                log.info("Tencent realtime ASR completed voiceSessionId={}", voiceId);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("Timed out waiting for Tencent realtime ASR completion", unwrap(ex));
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client closed");
            }
        }
    }

    private static final class ProtocolListener implements WebSocket.Listener {
        private final String voiceId;
        private final StreamingAsrListener listener;
        private final ObjectMapper objectMapper;
        private final StringBuilder textBuffer = new StringBuilder();
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> complete = new CompletableFuture<>();
        private final AtomicBoolean terminal = new AtomicBoolean();

        private ProtocolListener(String voiceId, StreamingAsrListener listener, ObjectMapper objectMapper) {
            this.voiceId = voiceId;
            this.listener = listener;
            this.objectMapper = objectMapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!complete.isDone() && terminal.compareAndSet(false, true)) {
                IllegalStateException error = new IllegalStateException(
                    "Tencent realtime ASR connection closed before final result: " + statusCode + " " + reason
                );
                ready.completeExceptionally(error);
                complete.completeExceptionally(error);
                listener.onError(error);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(error);
        }

        private void handleMessage(String message) {
            try {
                JsonNode root = objectMapper.readTree(message);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    throw new IllegalStateException(
                        "Tencent realtime ASR failed code=" + code + " message=" + root.path("message").asText("")
                    );
                }
                ready.complete(null);
                JsonNode result = root.get("result");
                if (result != null && result.isObject()) {
                    int sliceType = result.path("slice_type").asInt(-1);
                    if (sliceType == 1 || sliceType == 2) {
                        listener.onResult(new StreamingAsrResult(
                            result.path("voice_text_str").asText(""),
                            sliceType == 2,
                            result.path("index").asInt(0),
                            result.path("start_time").asLong(0),
                            result.path("end_time").asLong(0)
                        ));
                    }
                }
                if (root.path("final").asInt(0) == 1 && terminal.compareAndSet(false, true)) {
                    complete.complete(null);
                    listener.onComplete();
                }
            } catch (RuntimeException | java.io.IOException ex) {
                fail(ex);
            }
        }

        private void awaitReady(int timeoutMs) throws Exception {
            ready.get(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private void awaitComplete(int timeoutMs) throws Exception {
            complete.get(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private void fail(Throwable error) {
            if (terminal.compareAndSet(false, true)) {
                ready.completeExceptionally(error);
                complete.completeExceptionally(error);
                listener.onError(error);
            }
        }
    }
}
