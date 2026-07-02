package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FallbackAgent implements ChatAgent {
    private final LlmAgentResponder responder;


    @Autowired
    public FallbackAgent(LlmAgentResponder responder) {
        this.responder = responder;
    }

    @Override
    public RouteTarget target() {
        return RouteTarget.FALLBACK_AGENT;
    }

    @Override
    public String answer(ChatRequest request, RouteDecision decision) {
        return responder.answer(target(), request, decision,
            "兜底助手：你好，当前服务暂时无法完整处理这个问题，我已记录你的诉求。"
                + "你可以换一种说法、补充信息，或选择转人工客服。"
        );
    }
}