package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SafetyAgent implements ChatAgent {
    private final LlmAgentResponder responder;


    @Autowired
    public SafetyAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.SAFETY_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "安全应急助手：你好，我负责燃气安全和应急处置。"
                + "如果怀疑燃气泄漏，请先关闭表前阀门，打开门窗通风，不要开关电器或使用明火，并到室外拨打燃气抢修电话。"
                + "你描述的问题是：" + request.message()
        );
    }
}