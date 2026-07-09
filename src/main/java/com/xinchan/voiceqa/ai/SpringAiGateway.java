package com.xinchan.voiceqa.ai;

import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class SpringAiGateway {
    private final StreamingChatModelClient chatModelClient;

    public SpringAiGateway(StreamingChatModelClient chatModelClient) {
        this.chatModelClient = chatModelClient;
    }

    public String streamAsText(ChatModelRequest request) {
        return chatModelClient.streamAsText(request);
    }

    public String streamAsText(ChatModelRequest request, Consumer<String> deltaConsumer) {
        return chatModelClient.streamAsText(request, deltaConsumer);
    }
}