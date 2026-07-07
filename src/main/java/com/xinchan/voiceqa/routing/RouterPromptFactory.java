package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.springframework.stereotype.Component;

@Component
public class RouterPromptFactory {

    public String systemPrompt() {
        return """
            你是中文燃气客服助手的路由 Agent。
            只能返回 JSON，不要返回解释性文本。
            JSON 字段必须包含：
            rewrittenQuestion, intent, targetAgent, shouldStayInCurrentAgent, confidence, reason。
            targetAgent 只能是 PAYMENT_AGENT, SAFETY_AGENT, BUSINESS_DECISION_AGENT, RAG_AGENT, CLARIFICATION_AGENT, FALLBACK_AGENT。
            用户意图不完整或无法判断时，使用 CLARIFICATION_AGENT。
            """;
    }

    public String userPrompt(ChatRequest request, ConversationState state) {
        return """
            conversationId: %s
            userId: %s
            currentAgent: %s
            userMessage: %s
            """.formatted(
            request.conversationId(),
            request.userId(),
            state.currentAgent(),
            request.message()
        );
    }
}
