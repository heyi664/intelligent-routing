package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentAgent implements ChatAgent {
    private final LlmAgentResponder responder;

    public PaymentAgent() {
        this(LlmAgentResponder.localOnly());
    }

    @Autowired
    public PaymentAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.PAYMENT_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "缴费助手：你好，我可以帮你处理天然气缴费、充值、账单和欠费查询。"
                + "你刚才问的是：" + request.message()
                + "。如果要继续办理，请提供户号、手机号或账单月份。"
        );
    }
}