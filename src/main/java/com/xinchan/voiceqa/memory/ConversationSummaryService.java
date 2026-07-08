package com.xinchan.voiceqa.memory;

public interface ConversationSummaryService {
    String latestSummary(String conversationId);

    void summarizeIfNeeded(String conversationId, String userId);
}