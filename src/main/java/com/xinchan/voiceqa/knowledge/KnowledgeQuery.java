package com.xinchan.voiceqa.knowledge;

public record KnowledgeQuery(
    String question,
    int topK
) {
}
