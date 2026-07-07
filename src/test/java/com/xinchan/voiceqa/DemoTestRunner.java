package com.xinchan.voiceqa;

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
import com.xinchan.voiceqa.asr.AsrResult;
import com.xinchan.voiceqa.asr.TencentAsrConfig;
import com.xinchan.voiceqa.asr.TencentAsrSdkClient;
import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository;
import com.xinchan.voiceqa.qa.InMemoryFastQaService;
import com.xinchan.voiceqa.routing.AgentSwitchPolicy;
import com.xinchan.voiceqa.routing.RuleBasedRouterAgent;
import com.xinchan.voiceqa.routing.RouterService;
import com.xinchan.voiceqa.routing.RouteTarget;
import com.xinchan.voiceqa.voice.VoiceChatRequest;
import com.xinchan.voiceqa.voice.VoicePipelineService;

import java.util.List;

public class DemoTestRunner {

    public static void main(String[] args) {
        DemoTestRunner runner = new DemoTestRunner();
        runner.qaHitBypassesAgentRuntime();
        runner.routerSwitchesFromPaymentToSafetyAgent();
        runner.voicePipelineRoutesOnlyStableTranscript();
        runner.tencentSdkClientWrapsSdkResultAsStableTranscript();
        runner.tencentSdkConfigRejectsMissingSecret();
        runner.springBeanClassesAreAnnotated();
        System.out.println("All demo tests passed.");
    }

    private void qaHitBypassesAgentRuntime() {
        TestFixture fixture = newFixture();

        ChatResponse response = fixture.routerService.route(new ChatRequest(
            "c-qa",
            "u-1",
            "天然气缴费怎么操作？"
        ));

        assertEquals("QA", response.source(), "QA hit should use fast QA source");
        assertEquals(RouteTarget.QA_AGENT, response.targetAgent(), "QA hit should target QA agent");
        assertEquals(0, fixture.agentRuntime.executionCount(), "QA hit should bypass model/agent runtime");
        assertContains(response.answer(), "缴费", "QA answer should mention payment");
    }

    private void routerSwitchesFromPaymentToSafetyAgent() {
        TestFixture fixture = newFixture();
        fixture.repository.saveCurrentAgent("c-route", RouteTarget.PAYMENT_AGENT);

        ChatResponse response = fixture.routerService.route(new ChatRequest(
            "c-route",
            "u-1",
            "管道泄漏怎么处理？"
        ));

        assertEquals(RouteTarget.SAFETY_AGENT, response.targetAgent(), "Safety question should switch to safety agent");
        assertEquals(1, fixture.agentRuntime.executionCount(), "Non-QA route should execute one agent");
        assertEquals(RouteTarget.SAFETY_AGENT.name(), response.source(), "Demo agent source should identify executed agent");
    }

    private void voicePipelineRoutesOnlyStableTranscript() {
        TestFixture fixture = newFixture();
        VoicePipelineService voicePipelineService = new VoicePipelineService(
            request -> List.of(
                new AsrResult(request.voiceSessionId(), "天然气", false, 0.60, 120),
                new AsrResult(request.voiceSessionId(), "天然气缴费怎么操作？", true, 0.96, 420)
            ),
            fixture.routerService
        );

        ChatResponse response = voicePipelineService.handle(new VoiceChatRequest(
            "v-1",
            "c-voice",
            "u-1",
            new byte[] {1, 2, 3}
        ));

        assertEquals("QA", response.source(), "Stable ASR transcript should route through QA path");
        assertEquals(RouteTarget.QA_AGENT, response.targetAgent(), "Voice payment question should target QA agent");
        assertEquals(0, fixture.agentRuntime.executionCount(), "Voice QA hit should still bypass agent runtime");
    }

    private void tencentSdkClientWrapsSdkResultAsStableTranscript() {
        TencentAsrConfig config = new TencentAsrConfig(
            "secret-id",
            "secret-key",
            "ap-guangzhou",
            "16k_zh",
            "wav",
            16000,
            5000
        );
        TencentAsrSdkClient client = new TencentAsrSdkClient(config, (audioBytes, requestConfig) -> {
            assertEquals("16k_zh", requestConfig.engineModelType(), "SDK invoker should receive engine model");
            assertEquals("wav", requestConfig.voiceFormat(), "SDK invoker should receive voice format");
            assertEquals(3, audioBytes.length, "SDK invoker should receive original audio bytes");
            return "管道泄漏怎么处理？";
        });

        List<AsrResult> results = client.transcribe(new VoiceChatRequest(
            "v-sdk",
            "c-sdk",
            "u-1",
            new byte[] {7, 8, 9}
        ).toVoiceInputEvent());

        assertEquals(1, results.size(), "SDK client should return one stable transcript");
        assertEquals("管道泄漏怎么处理？", results.get(0).transcript(), "SDK result text should be preserved");
        assertEquals(true, results.get(0).stable(), "SDK result should be stable");
        assertEquals("v-sdk", results.get(0).voiceSessionId(), "Voice session id should be preserved");
    }

    private void tencentSdkConfigRejectsMissingSecret() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TencentAsrConfig("", "secret-key", "ap-guangzhou", "16k_zh", "wav", 16000, 5000).validate(),
            "Missing Tencent SecretId should fail fast"
        );
    }


    private void springBeanClassesAreAnnotated() {
        assertHasAnnotation("Service", com.xinchan.voiceqa.routing.RouterService.class, "RouterService should be a Spring service");
        assertHasAnnotation("Service", com.xinchan.voiceqa.voice.VoicePipelineService.class, "VoicePipelineService should be a Spring service");
        assertHasAnnotation("Repository", com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository.class, "State repository should be a Spring repository");
        assertHasAnnotation("Service", com.xinchan.voiceqa.qa.InMemoryFastQaService.class, "Fast QA service should be a Spring service");
        assertHasAnnotation("Service", com.xinchan.voiceqa.agent.MockAgentRuntime.class, "Agent runtime should be a Spring service");
    }
    private TestFixture newFixture() {
        ConversationStateRepository repository = new InMemoryConversationStateRepository();
        MockAgentRuntime agentRuntime = new MockAgentRuntime(repository, localAgents());
        RouterService routerService = new RouterService(
            new InMemoryFastQaService(),
            new RuleBasedRouterAgent(),
            new AgentSwitchPolicy(new com.xinchan.voiceqa.api.ChatProperties()),
            repository,
            agentRuntime,
            ChatProperties.router()
        );
        return new TestFixture(repository, agentRuntime, routerService);
    }


    private LlmAgentResponder localResponder() {
        return new LlmAgentResponder(
            new SpringAiGateway(request -> {
                throw new IllegalStateException("local test responder");
            }),
            new com.xinchan.voiceqa.agent.AgentPromptFactory()
        );
    }

    private List<ChatAgent> localAgents() {
        return List.of(
            new PaymentAgent(localResponder()),
            new SafetyAgent(localResponder()),
            new BusinessDecisionAgent(localResponder()),
            new RagAgent(localResponder()),
            new ClarificationAgent(localResponder()),
            new FallbackAgent(localResponder())
        );
    }

    private void assertHasAnnotation(String simpleName, Class<?> type, String message) {
        for (java.lang.annotation.Annotation annotation : type.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(simpleName)) {
                return;
            }
        }
        throw new AssertionError(message + " missingAnnotation=[" + simpleName + "] type=[" + type.getName() + "]");
    }
    private void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private void assertContains(String actual, String expectedPart, String message) {
        if (actual == null || !actual.contains(expectedPart)) {
            throw new AssertionError(message + " actual=[" + actual + "] expectedPart=[" + expectedPart + "]");
        }
    }

    private void assertThrows(Class<? extends Throwable> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + " expectedException=[" + expectedType.getName()
                + "] actualException=[" + actual.getClass().getName() + "]");
        }
        throw new AssertionError(message + " expectedException=[" + expectedType.getName() + "]");
    }

    private record TestFixture(
        ConversationStateRepository repository,
        MockAgentRuntime agentRuntime,
        RouterService routerService
    ) {
    }
}