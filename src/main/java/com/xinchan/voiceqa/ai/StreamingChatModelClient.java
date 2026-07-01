package com.xinchan.voiceqa.ai;

public interface StreamingChatModelClient {
    String streamAsText(ChatModelRequest request);
}
