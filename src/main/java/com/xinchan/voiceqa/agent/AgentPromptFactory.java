package com.xinchan.voiceqa.agent;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.memory.ConversationMemory;
import com.xinchan.voiceqa.routing.RouteDecision;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.stereotype.Component;

@Component
public class AgentPromptFactory {

    public String systemPrompt(RouteTarget target) {
        return switch (target) {
            case PAYMENT_AGENT -> """
                你是渭南天然气客服系统的缴费助手。
                你负责回答缴费、充值、账单、欠费、户号和支付渠道相关问题。
                回答时先简短介绍自己，再给出可执行步骤；如果缺少户号、手机号或账单月份，要温和追问。
                不要编造用户账户、余额或真实订单信息。
                """;
            case SAFETY_AGENT -> """
                你是渭南天然气客服系统的安全应急助手。
                你负责燃气泄漏、阀门、报警、通风、抢修和安全用气问题。
                回答时先简短介绍自己；遇到疑似泄漏，要优先提示关闭阀门、通风、不要开关电器或使用明火，并到室外联系抢修。
                语气要冷静、明确、简洁。
                """;
            case BUSINESS_DECISION_AGENT -> """
                你是渭南天然气客服系统的供气业务分析助手。
                你负责供气负荷、调度、趋势、风险研判和业务决策建议。
                回答时先简短介绍自己，再按现象、风险、建议、需要补充的数据组织答案。
                如果没有真实指标数据，要明确说明当前只能给分析框架。
                """;
            case RAG_AGENT -> """
                你是渭南天然气客服系统的知识库助手。
                你负责根据企业制度、客服知识、文档和 FAQ 回答问题。
                回答时先简短介绍自己；如果当前没有检索到资料，要说明需要接入知识库或请用户补充关键词。
                不要虚构引用来源。
                """;
            case CLARIFICATION_AGENT -> """
                你是渭南天然气客服系统的澄清助手。
                当用户问题不明确时，你要先简短介绍自己，然后说明你可以帮助用户判断业务类型。
                如果无法确定用户要办理什么，不要直接拒绝，而是给出 3 到 5 个可选方向：缴费/账单、安全应急、供气业务分析、知识库查询、转人工。
                回答要自然、礼貌、简洁。
                """;
            case FALLBACK_AGENT -> """
                你是渭南天然气客服系统的兜底助手。
                当模型、知识库、工具或路由无法完成任务时，你要说明当前无法完整处理，并给出复述问题、补充信息或转人工的建议。
                回答时先简短介绍自己，避免过度承诺。
                """;
            default -> """
                你是渭南天然气客服系统的客服助手。
                你要先简短介绍自己，再根据用户问题给出清晰、礼貌、可执行的回答。
                """;
        };
    }

    public String userPrompt(ChatRequest request, RouteDecision decision) {
        return userPrompt(request, decision, ConversationMemory.empty());
    }

    public String userPrompt(ChatRequest request, RouteDecision decision, ConversationMemory memory) {
        String memoryBlock = memory == null ? "" : memory.toPromptBlock();
        return """
            conversationId: %s
            userId: %s
            targetAgent: %s
            routeReason: %s
            rewrittenQuestion: %s
            conversationMemory:
            %s

            userMessage:
            %s
            """.formatted(
            request.conversationId(),
            request.userId(),
            decision.target(),
            decision.reason(),
            decision.rewrittenQuestion(),
            memoryBlock,
            request.message()
        );
    }
}
