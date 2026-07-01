package com.xinchan.voiceqa.knowledge;

import java.util.List;

public interface KnowledgeBaseClient {
    List<KnowledgeChunk> retrieve(KnowledgeQuery query);
}
