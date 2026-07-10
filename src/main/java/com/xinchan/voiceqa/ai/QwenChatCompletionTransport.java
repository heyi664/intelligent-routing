package com.xinchan.voiceqa.ai;

import java.util.function.Consumer;

public interface QwenChatCompletionTransport {
    QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request);

    default QwenChatCompletionResponse complete(
        String baseUrl,
        String apiKey,
        QwenChatCompletionRequest request,
        int connectTimeoutMs,
        int readTimeoutMs
    ) {
        return complete(baseUrl, apiKey, request);
    }

    default String stream(String baseUrl, String apiKey, QwenChatCompletionRequest request, Consumer<String> deltaConsumer) {
        QwenChatCompletionResponse response = complete(baseUrl, apiKey, request);
        String content = response == null ? "" : response.firstContent();
        if (content != null && !content.isBlank()) {
            deltaConsumer.accept(content);
        }
        return content;
    }

    default String stream(
        String baseUrl,
        String apiKey,
        QwenChatCompletionRequest request,
        Consumer<String> deltaConsumer,
        int connectTimeoutMs,
        int readTimeoutMs
    ) {
        return stream(baseUrl, apiKey, request, deltaConsumer);
    }
}
