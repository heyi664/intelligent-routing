package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;

public interface RouterAgent {
    RouteCandidate classify(ChatRequest request, ConversationState state);
}
