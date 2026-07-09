package com.xinchan.voiceqa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class QwenRestClientTransport implements QwenChatCompletionTransport {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public String stream(String baseUrl, String apiKey, QwenChatCompletionRequest request, Consumer<String> deltaConsumer) {
        StringBuilder answer = new StringBuilder();
        restClient.post()
            .uri(normalizeBaseUrl(baseUrl) + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .body(request)
            .exchange((httpRequest, response) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring("data:".length()).trim();
                        if (data.isBlank() || "[DONE]".equals(data)) {
                            continue;
                        }
                        String delta = extractDelta(data);
                        if (delta != null && !delta.isEmpty()) {
                            answer.append(delta);
                            deltaConsumer.accept(delta);
                        }
                    }
                }
                return null;
            });
        return answer.toString();
    }

    private String extractDelta(String data) throws IOException {
        JsonNode root = objectMapper.readTree(data);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode delta = choices.get(0).get("delta");
        if (delta == null || delta.get("content") == null) {
            return "";
        }
        return delta.get("content").asText("");
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.ai.base-url is required");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}