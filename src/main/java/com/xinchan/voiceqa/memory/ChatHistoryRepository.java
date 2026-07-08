package com.xinchan.voiceqa.memory;

import java.util.List;

public interface ChatHistoryRepository {
    ChatTurn saveTurn(ChatTurn turn);

    List<ChatTurn> findRecentTurns(String conversationId, int limit);
}