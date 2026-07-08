package com.xinchan.voiceqa.conversation;

import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryConversationStateRepository implements ConversationStateRepository {
    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    @Override
    public ConversationState findOrCreate(String conversationId, String userId) {
        return states.computeIfAbsent(conversationId, id -> new ConversationState(
            conversationId,
            userId,
            RouteTarget.MAIN_ROUTER,
            Instant.now()
        ));
    }

    @Override
    public void saveCurrentAgent(String conversationId, RouteTarget target) {
        ConversationState current = states.get(conversationId);
        String userId = current == null ? "" : current.userId();
        states.put(conversationId, new ConversationState(conversationId, userId, target, Instant.now()));
    }
}
