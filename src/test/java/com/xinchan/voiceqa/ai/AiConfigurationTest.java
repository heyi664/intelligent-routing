package com.xinchan.voiceqa.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiConfiguration.class));

    @Test
    void defaultModelIsQwenFlashAndApiKeyCanComeFromConfigurationProperty() {
        contextRunner
            .withPropertyValues("app.ai.api-key=test-api-key")
            .run(context -> {
                assertThat(context).hasSingleBean(StreamingChatModelClient.class);
                AiProperties properties = context.getBean(AiProperties.class);
                assertThat(properties.getProvider()).isEqualTo("qwen");
                assertThat(properties.getModel()).isEqualTo("qwen3.6-flash");
                assertThat(properties.getApiKey()).isEqualTo("test-api-key");
                assertThat(properties.getConnectTimeoutMs()).isEqualTo(3000);
                assertThat(properties.getRouterTimeoutMs()).isEqualTo(6000);
                assertThat(properties.getAgentTimeoutMs()).isEqualTo(30000);
                assertThat(properties.getRouterMaxRetries()).isEqualTo(1);
                assertThat(properties.getAgentMaxRetries()).isEqualTo(1);
            });
    }

    @Test
    void bindsPurposeSpecificTimeoutAndRetryConfiguration() {
        contextRunner
            .withPropertyValues(
                "app.ai.api-key=test-api-key",
                "app.ai.connect-timeout-ms=1111",
                "app.ai.router-timeout-ms=2222",
                "app.ai.agent-timeout-ms=3333",
                "app.ai.router-max-retries=2",
                "app.ai.agent-max-retries=4",
                "app.ai.retry-backoff-ms=55"
            )
            .run(context -> {
                AiProperties properties = context.getBean(AiProperties.class);
                assertThat(properties.getConnectTimeoutMs()).isEqualTo(1111);
                assertThat(properties.getRouterTimeoutMs()).isEqualTo(2222);
                assertThat(properties.getAgentTimeoutMs()).isEqualTo(3333);
                assertThat(properties.getRouterMaxRetries()).isEqualTo(2);
                assertThat(properties.getAgentMaxRetries()).isEqualTo(4);
                assertThat(properties.getRetryBackoffMs()).isEqualTo(55);
            });
    }

    @Test
    void qwenApiKeyCanBeOverriddenByConfigurationProperty() {
        contextRunner
            .withPropertyValues("app.ai.api-key=alias-api-key")
            .run(context -> {
                AiProperties properties = context.getBean(AiProperties.class);
                assertThat(properties.getApiKey()).isEqualTo("alias-api-key");
            });
    }
}
