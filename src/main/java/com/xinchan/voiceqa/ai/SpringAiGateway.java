package com.xinchan.voiceqa.ai;

import org.springframework.stereotype.Service;

@Service
public class SpringAiGateway {
    private final StreamingChatModelClient chatModelClient;

    public SpringAiGateway(StreamingChatModelClient chatModelClient) {
        this.chatModelClient = chatModelClient;
    }

    public String streamAsText(ChatModelRequest request) {
        return chatModelClient.streamAsText(request);
    }
}