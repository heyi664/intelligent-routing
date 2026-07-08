package com.xinchan.voiceqa.memory;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationMemoryService {
    private final ChatHistoryRepository chatHistoryRepository;
    private final ConversationSummaryService summaryService;
    private final MemoryProperties properties;

    public ConversationMemoryService(
        ChatHistoryRepository chatHistoryRepository,
        ConversationSummaryService summaryService,
        MemoryProperties properties
    ) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.summaryService = summaryService;
        this.properties = properties;
    }

    public ChatTurn recordTurn(ChatTurn turn) {
        ChatTurn saved = chatHistoryRepository.saveTurn(turn);
        summaryService.summarizeIfNeeded(saved.conversationId(), saved.userId());
        return saved;
    }

    public ConversationMemory loadForPrompt(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return ConversationMemory.empty();
        }
        String summary = summaryService.latestSummary(conversationId);
        List<ChatTurn> recentTurns = chatHistoryRepository.findRecentTurns(conversationId, properties.getRecentTurnLimit());
        return new ConversationMemory(summary, recentTurns);
    }
}