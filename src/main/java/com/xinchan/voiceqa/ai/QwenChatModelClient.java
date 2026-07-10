package com.xinchan.voiceqa.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class QwenChatModelClient implements StreamingChatModelClient {
    private static final Logger log = LoggerFactory.getLogger(QwenChatModelClient.class);

    private final AiProperties properties;
    private final QwenChatCompletionTransport transport;

    public QwenChatModelClient(AiProperties properties, QwenChatCompletionTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public String streamAsText(ChatModelRequest request) {
        String apiKey = requiredApiKey();
        CallPolicy policy = policy(request);
        QwenChatCompletionResponse response = executeWithRetry(request, policy, null, () -> transport.complete(
            properties.getBaseUrl(),
            apiKey,
            new QwenChatCompletionRequest(properties.getModel(), messages(request), false),
            positive(properties.getConnectTimeoutMs(), properties.getTimeoutMs()),
            policy.readTimeoutMs()
        ));
        String content = response == null ? "" : response.firstContent();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Qwen returned empty chat completion content");
        }
        return content;
    }

    @Override
    public String streamAsText(ChatModelRequest request, Consumer<String> deltaConsumer) {
        String apiKey = requiredApiKey();
        CallPolicy policy = policy(request);
        AtomicBoolean emitted = new AtomicBoolean();
        Consumer<String> trackingConsumer = delta -> {
            emitted.set(true);
            deltaConsumer.accept(delta);
        };
        String content = executeWithRetry(request, policy, emitted, () -> transport.stream(
            properties.getBaseUrl(),
            apiKey,
            new QwenChatCompletionRequest(properties.getModel(), messages(request), true),
            trackingConsumer,
            positive(properties.getConnectTimeoutMs(), properties.getTimeoutMs()),
            policy.readTimeoutMs()
        ));
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Qwen returned empty chat completion content");
        }
        return content;
    }

    private <T> T executeWithRetry(
        ChatModelRequest request,
        CallPolicy policy,
        AtomicBoolean emitted,
        Supplier<T> operation
    ) {
        int maxAttempts = policy.maxRetries() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException ex) {
                if (emitted != null && emitted.get()) {
                    throw new PartialChatResponseException(ex);
                }
                if (attempt >= maxAttempts || !isRetryable(ex)) {
                    throw ex;
                }
                log.warn(
                    "Qwen call retry traceId={} purpose={} attempt={} maxAttempts={} errorType={} errorMessage={}",
                    traceId(request),
                    purpose(request),
                    attempt,
                    maxAttempts,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
                );
                backoff(attempt);
            }
        }
        throw new IllegalStateException("Unreachable Qwen retry state");
    }

    private CallPolicy policy(ChatModelRequest request) {
        if (purpose(request) == ChatModelPurpose.ROUTER) {
            return new CallPolicy(
                positive(properties.getRouterTimeoutMs(), properties.getTimeoutMs()),
                nonNegative(properties.getRouterMaxRetries())
            );
        }
        return new CallPolicy(
            positive(properties.getAgentTimeoutMs(), properties.getTimeoutMs()),
            nonNegative(properties.getAgentMaxRetries())
        );
    }

    private void backoff(int attempt) {
        long delayMs = Math.max(0, properties.getRetryBackoffMs()) * attempt;
        if (delayMs == 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Qwen call", ex);
        }
    }

    private static boolean isRetryable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ResourceAccessException || current instanceof IOException) {
                return true;
            }
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 429 || responseException.getStatusCode().is5xxServerError();
            }
            current = current.getCause();
        }
        return false;
    }

    private static ChatModelPurpose purpose(ChatModelRequest request) {
        return request.purpose() == null ? ChatModelPurpose.AGENT : request.purpose();
    }

    private static String traceId(ChatModelRequest request) {
        return request.traceId() == null || request.traceId().isBlank() ? request.conversationId() : request.traceId();
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : Math.max(1, fallback);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
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

    private record CallPolicy(int readTimeoutMs, int maxRetries) {
    }
}
