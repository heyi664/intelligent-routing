package com.xinchan.voiceqa.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QwenChatModelClient implements StreamingChatModelClient {
    private final AiProperties properties;
    private final QwenChatCompletionTransport transport;

    public QwenChatModelClient(AiProperties properties, QwenChatCompletionTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public String streamAsText(ChatModelRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Qwen API key is required. Set DASHSCOPE_API_KEY or QWEN_API_KEY.");
        }

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

    private static List<Map<String, String>> messages(ChatModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        return messages;
    }
}