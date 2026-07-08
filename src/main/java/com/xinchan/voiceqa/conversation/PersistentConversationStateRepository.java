package com.xinchan.voiceqa.conversation;

import com.xinchan.voiceqa.memory.PgMemoryStore;
import com.xinchan.voiceqa.memory.RedisStateCache;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "true")
public class PersistentConversationStateRepository implements ConversationStateRepository {
    private final RedisStateCache redisStateCache;
    private final PgMemoryStore store;

    public PersistentConversationStateRepository(RedisStateCache redisStateCache, PgMemoryStore store) {
        this.redisStateCache = redisStateCache;
        this.store = store;
    }

    @Override
    public ConversationState findOrCreate(String conversationId, String userId) {
        return redisStateCache.get(conversationId)
            .or(() -> store.findState(conversationId))
            .map(state -> {
                redisStateCache.set(state);
                return state;
            })
            .orElseGet(() -> {
                ConversationState created = new ConversationState(conversationId, userId, RouteTarget.MAIN_ROUTER, Instant.now());
                store.saveState(created);
                redisStateCache.set(created);
                return created;
            });
    }

    @Override
    public void saveCurrentAgent(String conversationId, RouteTarget target) {
        ConversationState current = redisStateCache.get(conversationId)
            .or(() -> store.findState(conversationId))
            .orElse(new ConversationState(conversationId, "", RouteTarget.MAIN_ROUTER, Instant.now()));
        ConversationState updated = new ConversationState(conversationId, current.userId(), target, Instant.now());
        store.saveState(updated);
        redisStateCache.set(updated);
    }
}