package com.xinchan.voiceqa.ai;

import java.util.List;

public record QwenChatCompletionResponse(
    List<Choice> choices
) {
    public QwenChatCompletionResponse(String content) {
        this(List.of(new Choice(new Message("assistant", content))));
    }

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return "";
        }
        return choices.get(0).message().content();
    }

    public record Choice(Message message) {
    }

    public record Message(String role, String content) {
    }
}