package com.xinchan.voiceqa.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentConstructorInjectionTest {

    @Test
    void springChatAgentsDoNotExposeLocalOnlyNoArgConstructors() {
        List<Class<? extends ChatAgent>> agentTypes = List.of(
            PaymentAgent.class,
            SafetyAgent.class,
            BusinessDecisionAgent.class,
            RagAgent.class,
            ClarificationAgent.class,
            FallbackAgent.class
        );

        for (Class<? extends ChatAgent> agentType : agentTypes) {
            assertFalse(
                hasNoArgConstructor(agentType),
                agentType.getSimpleName() + " should require LlmAgentResponder injection"
            );
        }
    }

    @Test
    void agentRuntimeDoesNotExposeLocalOnlyConstructor() {
        for (java.lang.reflect.Constructor<?> constructor : MockAgentRuntime.class.getDeclaredConstructors()) {
            boolean localOnlyConstructor = constructor.getParameterCount() == 1
                && constructor.getParameterTypes()[0].getName().equals("com.xinchan.voiceqa.conversation.ConversationStateRepository");
            assertFalse(
                localOnlyConstructor,
                "MockAgentRuntime should require Spring-injected ChatAgent list"
            );
        }
    }
    @Test
    void llmAgentResponderDoesNotExposeLocalOnlyPath() {
        assertFalse(
            hasNoArgConstructor(LlmAgentResponder.class),
            "LlmAgentResponder should not have a local-only no-arg constructor"
        );
        for (java.lang.reflect.Method method : LlmAgentResponder.class.getDeclaredMethods()) {
            assertFalse(
                method.getName().equals("localOnly"),
                "LlmAgentResponder should not expose localOnly factory in main code"
            );
        }
    }

    private boolean hasNoArgConstructor(Class<?> type) {
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }
}