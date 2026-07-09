package com.xinchan.voiceqa.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
}