package com.xinchan.voiceqa.memory;

public class NoopConversationSummaryService implements ConversationSummaryService {
    @Override
    public String latestSummary(String conversationId) {
        return "";
    }

    @Override
    public void summarizeIfNeeded(String conversationId, String userId) {
        // Summary is an optional enhancement and is implemented by persistent memory.
    }
}
