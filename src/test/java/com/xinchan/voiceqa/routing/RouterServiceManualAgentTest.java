package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.agent.BusinessDecisionAgent;
import com.xinchan.voiceqa.agent.ChatAgent;
import com.xinchan.voiceqa.agent.ClarificationAgent;
import com.xinchan.voiceqa.agent.FallbackAgent;
import com.xinchan.voiceqa.agent.LlmAgentResponder;
import com.xinchan.voiceqa.agent.MockAgentRuntime;
import com.xinchan.voiceqa.agent.PaymentAgent;
import com.xinchan.voiceqa.agent.RagAgent;
import com.xinchan.voiceqa.agent.SafetyAgent;
import com.xinchan.voiceqa.ai.SpringAiGateway;
import com.xinchan.voiceqa.api.ChatProperties;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository;
import com.xinchan.voiceqa.qa.InMemoryFastQaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterServiceManualAgentTest {

    @Test
    void manualAgentModeSendsNonQaMessagesToConfiguredAgent() {
        InMemoryConversationStateRepository repository = new InMemoryConversationStateRepository();
        MockAgentRuntime agentRuntime = new MockAgentRuntime(repository, localAgents());
        RouterService routerService = new RouterService(
            new InMemoryFastQaService(),
            new RuleBasedRouterAgent(),
            new AgentSwitchPolicy(new com.xinchan.voiceqa.api.ChatProperties()),
            repository,
            agentRuntime,
            ChatProperties.manual(RouteTarget.PAYMENT_AGENT)
        );

        ChatResponse response = routerService.route(new ChatRequest(
            "c-manual-payment",
            "u-1",
            "管道泄漏怎么处理？"
        ));

        assertEquals(RouteTarget.PAYMENT_AGENT, response.targetAgent());
        assertEquals("PAYMENT_AGENT", response.source());
        assertTrue(response.answer().contains("缴费"));
        assertEquals(1, agentRuntime.executionCount());
    }

    @Test
    void manualAgentModeKeepsFastQaBeforeAgentRuntime() {
        InMemoryConversationStateRepository repository = new InMemoryConversationStateRepository();
        MockAgentRuntime agentRuntime = new MockAgentRuntime(repository, localAgents());
        RouterService routerService = new RouterService(
            new InMemoryFastQaService(),
            new RuleBasedRouterAgent(),
            new AgentSwitchPolicy(new com.xinchan.voiceqa.api.ChatProperties()),
            repository,
            agentRuntime,
            ChatProperties.manual(RouteTarget.SAFETY_AGENT)
        );

        ChatResponse response = routerService.route(new ChatRequest(
            "c-manual-qa",
            "u-1",
            "天然气缴费怎么操作？"
        ));

        assertEquals(RouteTarget.QA_AGENT, response.targetAgent());
        assertEquals("QA", response.source());
        assertEquals(0, agentRuntime.executionCount());
    }


    private LlmAgentResponder localResponder() {
        return new LlmAgentResponder(
            new SpringAiGateway(request -> {
                throw new IllegalStateException("local test responder");
            }),
            new com.xinchan.voiceqa.agent.AgentPromptFactory()
        );
    }

    private java.util.List<ChatAgent> localAgents() {
        return java.util.List.of(
            new PaymentAgent(localResponder()),
            new SafetyAgent(localResponder()),
            new BusinessDecisionAgent(localResponder()),
            new RagAgent(localResponder()),
            new ClarificationAgent(localResponder()),
            new FallbackAgent(localResponder())
        );
    }
}
