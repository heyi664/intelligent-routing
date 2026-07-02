package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClarificationAgent implements ChatAgent {
    private final LlmAgentResponder responder;

    public ClarificationAgent() {
        this(LlmAgentResponder.localOnly());
    }

    @Autowired
    public ClarificationAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.CLARIFICATION_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "澄清助手：你好，我可以帮你判断要办理的业务类型。"
                + "你可以问我缴费/账单、安全应急、供气分析、知识库查询，或说明需要转人工。"
        );
    }
}