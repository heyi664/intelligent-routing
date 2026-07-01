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