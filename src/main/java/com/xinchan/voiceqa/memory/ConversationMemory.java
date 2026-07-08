package com.xinchan.voiceqa.memory;

import java.util.List;

public record ConversationMemory(
    String summary,
    List<ChatTurn> recentTurns
) {
    public static ConversationMemory empty() {
        return new ConversationMemory("", List.of());
    }

    public String toPromptBlock() {
        StringBuilder builder = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            builder.append("summary: ").append(summary).append('\n');
        }
        if (recentTurns != null && !recentTurns.isEmpty()) {
            builder.append("recentTurns:\n");
            for (ChatTurn turn : recentTurns) {
                builder.append("U: ").append(nullToEmpty(turn.userMessage())).append('\n');
                builder.append("A: ").append(nullToEmpty(turn.assistantMessage())).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}