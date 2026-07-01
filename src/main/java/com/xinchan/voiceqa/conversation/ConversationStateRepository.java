package com.xinchan.voiceqa.conversation;

import com.xinchan.voiceqa.routing.RouteTarget;

public interface ConversationStateRepository {
    ConversationState findOrCreate(String conversationId, String userId);

    void saveCurrentAgent(String conversationId, RouteTarget target);
}
