package com.xinchan.voiceqa.ai;

public class PartialChatResponseException extends RuntimeException {
    public PartialChatResponseException(Throwable cause) {
        super("Qwen streaming response was interrupted after partial content", cause);
    }
}
