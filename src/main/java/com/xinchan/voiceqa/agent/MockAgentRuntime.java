package com.xinchan.voiceqa.agent;


import org.springframework.stereotype.Service;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.routing.RouteDecision;

@Service
public class MockAgentRuntime implements AgentRuntime {
    private final ConversationStateRepository stateRepository;
    private int executionCount;

    public MockAgentRuntime(ConversationStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @Override
    public ChatResponse execute(ChatRequest request, RouteDecision decision) {
        executionCount++;
        stateRepository.saveCurrentAgent(request.conversationId(), decision.target());

        // 当前 Runtime 只保持 Demo 简洁，后续这里会变成真实子 Agent 注册表。
        // TODO: dispatch to real QaAgent/RagAgent/SafetyAgent implementations.
        String answer = "DEMO_AGENT=" + decision.target()
            + " intentQuestion=\"" + decision.rewrittenQuestion()
            + "\" reason=\"" + decision.reason() + "\"";
        return new ChatResponse(request.conversationId(), decision.target(), answer, "AGENT_RUNTIME");
    }

    public int executionCount() {
        return executionCount;
    }
}

