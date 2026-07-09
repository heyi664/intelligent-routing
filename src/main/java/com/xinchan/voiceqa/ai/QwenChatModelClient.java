package com.xinchan.voiceqa.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class QwenChatModelClient implements StreamingChatModelClient {
    private final AiProperties properties;
    private final QwenChatCompletionTransport transport;

    public QwenChatModelClient(AiProperties properties, QwenChatCompletionTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public String streamAsText(ChatModelRequest request) {
        String apiKey = requiredApiKey();
        QwenChatCompletionResponse response = transport.complete(
            properties.getBaseUrl(),
            apiKey,
            new QwenChatCompletionRequest(properties.getModel(), messages(request), false)
        );
        String content = response == null ? "" : response.firstContent();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Qwen returned empty chat completion content");
        }
        return content;
    }

    @Override
    public String streamAsText(ChatModelRequest request, Consumer<String> deltaConsumer) {
        String apiKey = requiredApiKey();
        String content = transport.stream(
            properties.getBaseUrl(),
            apiKey,
            new QwenChatCompletionRequest(properties.getModel(), messages(request), true),
            deltaConsumer
        );
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Qwen returned empty chat completion content");
        }
        return content;
    }

    private String requiredApiKey() {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.ai.api-key is required.");
        }
        return apiKey;
    }

    private static List<Map<String, String>> messages(ChatModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        return messages;
    }
}