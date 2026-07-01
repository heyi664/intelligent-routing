package com.xinchan.voiceqa.knowledge;

public record KnowledgeChunk(
    String id,
    String source,
    String title,
    String content,
    double score
) {
}
