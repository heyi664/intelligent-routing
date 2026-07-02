package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BusinessDecisionAgent implements ChatAgent {
    private final LlmAgentResponder responder;


    @Autowired
    public BusinessDecisionAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.BUSINESS_DECISION_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "业务决策助手：你好，我负责供气负荷、异常趋势、风险等级和调度建议分析。"
                + "当前问题是：" + request.message()
                + "。Demo 阶段如果没有真实指标，我会先给出分析框架。"
        );
    }
}