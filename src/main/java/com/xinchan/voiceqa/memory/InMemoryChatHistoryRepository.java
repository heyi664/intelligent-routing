package com.xinchan.voiceqa.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryChatHistoryRepository implements ChatHistoryRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, List<ChatTurn>> turns = new ConcurrentHashMap<>();

    @Override
    public ChatTurn saveTurn(ChatTurn turn) {
        ChatTurn saved = new ChatTurn(
            turn.id() == null ? ids.getAndIncrement() : turn.id(),
            turn.conversationId(),
            turn.userId(),
            turn.userMessage(),
            turn.assistantMessage(),
            turn.targetAgent(),
            turn.source(),
            turn.createdAt()
        );
        turns.computeIfAbsent(saved.conversationId(), key -> new ArrayList<>()).add(saved);
        return saved;
    }

    @Override
    public List<ChatTurn> findRecentTurns(String conversationId, int limit) {
        List<ChatTurn> all = turns.getOrDefault(conversationId, List.of())
            .stream()
            .sorted(Comparator.comparing(ChatTurn::createdAt))
            .toList();
        int fromIndex = Math.max(0, all.size() - Math.max(0, limit));
        return new ArrayList<>(all.subList(fromIndex, all.size()));
    }
}
