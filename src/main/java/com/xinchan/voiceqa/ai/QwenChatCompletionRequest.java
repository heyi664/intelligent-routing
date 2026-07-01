package com.xinchan.voiceqa.ai;

import java.util.List;
import java.util.Map;

public record QwenChatCompletionRequest(
    String model,
    List<Map<String, String>> messages,
    Boolean stream
) {
}