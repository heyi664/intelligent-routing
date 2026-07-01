package com.xinchan.voiceqa.knowledge;


import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MockKnowledgeBaseClient implements KnowledgeBaseClient {

    @Override
    public List<KnowledgeChunk> retrieve(KnowledgeQuery query) {
        // TODO: replace with PostgreSQL + pgvector or enterprise knowledge-base HTTP API.
        return List.of(new KnowledgeChunk(
            "mock-001",
            "mock",
            "渭南天然气客服知识",
            "如遇燃气泄漏，应先关闭阀门，打开门窗，远离现场后联系抢修热线。",
            0.90
        ));
    }
}
