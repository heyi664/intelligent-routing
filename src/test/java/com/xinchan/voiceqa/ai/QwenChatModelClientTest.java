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
}