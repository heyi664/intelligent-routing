package com.xinchan.voiceqa.ai;

import org.springframework.web.client.RestClient;

public class QwenRestClientTransport implements QwenChatCompletionTransport {
    private final RestClient restClient;

    public QwenRestClientTransport(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request) {
        return restClient.post()
            .uri(normalizeBaseUrl(baseUrl) + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .body(request)
            .retrieve()
            .body(QwenChatCompletionResponse.class);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.ai.base-url is required");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}