package com.xinchan.voiceqa.ai;

public interface QwenChatCompletionTransport {
    QwenChatCompletionResponse complete(String baseUrl, String apiKey, QwenChatCompletionRequest request);
}