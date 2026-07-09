package com.xinchan.voiceqa.ai;

import java.util.function.Consumer;

public interface StreamingChatModelClient {
    String streamAsText(ChatModelRequest request);

    default String streamAsText(ChatModelRequest request, Consumer<String> deltaConsumer) {
        String answer = streamAsText(request);
        if (answer != null && !answer.isBlank()) {
            deltaConsumer.accept(answer);
        }
        return answer;
    }
}