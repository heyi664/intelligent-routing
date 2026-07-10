package com.xinchan.voiceqa.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QwenChatModelClientTest {

    @Test
    void sendsOpenAiCompatibleChatCompletionRequest() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("qwen3.6-flash");
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        AtomicReference<QwenChatCompletionRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedBaseUrl = new AtomicReference<>();
        QwenChatModelClient client = new QwenChatModelClient(properties, (baseUrl, apiKey, request) -> {
            capturedBaseUrl.set(baseUrl);
            capturedApiKey.set(apiKey);
            capturedRequest.set(request);
            return new QwenChatCompletionResponse("模型回答");
        });

        String result = client.streamAsText(new ChatModelRequest(
            "你是客服路由助手",
            "天然气缴费怎么操作？",
            "conversation-1",
            "trace-1"
        ));

        assertThat(result).isEqualTo("模型回答");
        assertThat(capturedBaseUrl.get()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(capturedApiKey.get()).isEqualTo("test-api-key");
        assertThat(capturedRequest.get().model()).isEqualTo("qwen3.6-flash");
        assertThat(capturedRequest.get().messages()).containsExactly(
            Map.of("role", "system", "content", "你是客服路由助手"),
            Map.of("role", "user", "content", "天然气缴费怎么操作？")
        );
    }
    @Test
    void trimsApiKeyBeforeSendingAuthorizationHeader() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test\n-api-key\r\n");
        properties.setModel("qwen3.6-flash");
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        QwenChatModelClient client = new QwenChatModelClient(properties, (baseUrl, apiKey, request) -> {
            capturedApiKey.set(apiKey);
            return new QwenChatCompletionResponse("ok");
        });

        String result = client.streamAsText(new ChatModelRequest("system", "user", "conversation-1", "trace-1"));

        assertThat(result).isEqualTo("ok");
        assertThat(capturedApiKey.get()).isEqualTo("test-api-key");
    }
    @Test
    void sendsStreamingChatCompletionRequestAndCollectsDeltas() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("qwen3.6-flash");
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        AtomicReference<QwenChatCompletionRequest> capturedRequest = new AtomicReference<>();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("streaming should not call complete");
            }

            @Override
            public String stream(String baseUrl, String apiKey, QwenChatCompletionRequest request, java.util.function.Consumer<String> deltaConsumer) {
                capturedRequest.set(request);
                deltaConsumer.accept("hello ");
                deltaConsumer.accept("world");
                return "hello world";
            }
        });
        java.util.List<String> deltas = new java.util.ArrayList<>();

        String result = client.streamAsText(new ChatModelRequest("system", "user", "conversation-1", "trace-1"), deltas::add);

        assertThat(result).isEqualTo("hello world");
        assertThat(deltas).containsExactly("hello ", "world");
        assertThat(capturedRequest.get().stream()).isTrue();
    }

    @Test
    void usesPurposeSpecificReadTimeoutsAndSharedConnectTimeout() {
        AiProperties properties = properties();
        properties.setConnectTimeoutMs(1234);
        properties.setRouterTimeoutMs(2345);
        properties.setAgentTimeoutMs(3456);
        properties.setRouterMaxRetries(0);
        properties.setAgentMaxRetries(0);
        List<List<Integer>> timeouts = new ArrayList<>();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("purpose-specific calls should include timeout values");
            }

            @Override
            public QwenChatCompletionResponse complete(
                String baseUrl,
                String apiKey,
                QwenChatCompletionRequest request,
                int connectTimeoutMs,
                int readTimeoutMs
            ) {
                timeouts.add(List.of(connectTimeoutMs, readTimeoutMs));
                return new QwenChatCompletionResponse("ok");
            }
        });

        client.streamAsText(new ChatModelRequest("system", "router", "c-1", "trace-router", ChatModelPurpose.ROUTER));
        client.streamAsText(new ChatModelRequest("system", "agent", "c-1", "trace-agent"));

        assertThat(timeouts).containsExactly(List.of(1234, 2345), List.of(1234, 3456));
    }

    @Test
    void retriesTemporaryIoFailureUpToConfiguredLimit() {
        AiProperties properties = properties();
        properties.setRouterMaxRetries(2);
        properties.setRetryBackoffMs(0);
        AtomicInteger calls = new AtomicInteger();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("timeout overload expected");
            }

            @Override
            public QwenChatCompletionResponse complete(
                String baseUrl,
                String apiKey,
                QwenChatCompletionRequest request,
                int connectTimeoutMs,
                int readTimeoutMs
            ) {
                if (calls.incrementAndGet() < 3) {
                    throw new ResourceAccessException("connection reset", new IOException("reset"));
                }
                return new QwenChatCompletionResponse("recovered");
            }
        });

        String answer = client.streamAsText(
            new ChatModelRequest("system", "router", "c-1", "trace-retry", ChatModelPurpose.ROUTER)
        );

        assertThat(answer).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryAuthenticationFailure() {
        AiProperties properties = properties();
        properties.setAgentMaxRetries(3);
        properties.setRetryBackoffMs(0);
        AtomicInteger calls = new AtomicInteger();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("timeout overload expected");
            }

            @Override
            public QwenChatCompletionResponse complete(
                String baseUrl,
                String apiKey,
                QwenChatCompletionRequest request,
                int connectTimeoutMs,
                int readTimeoutMs
            ) {
                calls.incrementAndGet();
                throw HttpClientErrorException.create(
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8
                );
            }
        });

        assertThatThrownBy(() -> client.streamAsText(new ChatModelRequest("system", "agent", "c-1", "trace-auth")))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesRateLimitAndServerErrors() {
        AiProperties properties = properties();
        properties.setRouterMaxRetries(2);
        properties.setRetryBackoffMs(0);
        AtomicInteger calls = new AtomicInteger();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("timeout overload expected");
            }

            @Override
            public QwenChatCompletionResponse complete(
                String baseUrl,
                String apiKey,
                QwenChatCompletionRequest request,
                int connectTimeoutMs,
                int readTimeoutMs
            ) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                    );
                }
                if (call == 2) {
                    throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                    );
                }
                return new QwenChatCompletionResponse("recovered");
            }
        });

        String answer = client.streamAsText(
            new ChatModelRequest("system", "router", "c-1", "trace-http-retry", ChatModelPurpose.ROUTER)
        );

        assertThat(answer).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryStreamingCallAfterAnyDeltaWasEmitted() {
        AiProperties properties = properties();
        properties.setAgentMaxRetries(3);
        properties.setRetryBackoffMs(0);
        AtomicInteger calls = new AtomicInteger();
        QwenChatModelClient client = new QwenChatModelClient(properties, new QwenChatCompletionTransport() {
            @Override
            public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
                throw new AssertionError("stream overload expected");
            }

            @Override
            public String stream(
                String baseUrl,
                String apiKey,
                QwenChatCompletionRequest request,
                java.util.function.Consumer<String> deltaConsumer,
                int connectTimeoutMs,
                int readTimeoutMs
            ) {
                calls.incrementAndGet();
                deltaConsumer.accept("partial");
                throw new ResourceAccessException("stream reset", new IOException("reset"));
            }
        });
        List<String> deltas = new ArrayList<>();

        assertThatThrownBy(() -> client.streamAsText(
            new ChatModelRequest("system", "agent", "c-1", "trace-partial"),
            deltas::add
        )).isInstanceOf(PartialChatResponseException.class);
        assertThat(calls).hasValue(1);
        assertThat(deltas).containsExactly("partial");
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl("https://example.test/v1");
        properties.setModel("qwen-test");
        return properties;
    }
}
