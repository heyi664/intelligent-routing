package com.xinchan.voiceqa.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "true")
public class JdbcChatHistoryRepository implements ChatHistoryRepository {
    private final PgMemoryStore store;

    public JdbcChatHistoryRepository(PgMemoryStore store) {
        this.store = store;
    }

    @Override
    public ChatTurn saveTurn(ChatTurn turn) {
        return store.saveTurn(turn);
    }

    @Override
    public List<ChatTurn> findRecentTurns(String conversationId, int limit) {
        return store.findRecentTurns(conversationId, limit);
    }
}