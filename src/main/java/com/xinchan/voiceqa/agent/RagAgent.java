package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RagAgent implements ChatAgent {
    private final LlmAgentResponder responder;


    @Autowired
    public RagAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.RAG_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "知识库助手：你好，我负责根据企业知识库和客服知识回答问题。"
                + "当前 Demo 还没有接入真实 RAG，问题已收到：" + request.message()
        );
    }

    @Override
    public String answerStreaming(ChatRequest request, RouteDecision decision, java.util.function.Consumer<String> deltaConsumer) {
        return responder.answerStreaming(target(), request, decision,
            "知识库助手：你好，我负责根据企业知识库和客服知识回答问题。"
                + "当前 Demo 还没有接入真实 RAG，问题已收到：" + request.message()
            , deltaConsumer);
    }
}