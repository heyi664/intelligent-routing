package com.xinchan.voiceqa.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiConfiguration.class));

    @Test
    void defaultModelIsQwenFlashAndApiKeyComesFromDashscopeEnvironmentName() {
        contextRunner
            .withPropertyValues("DASHSCOPE_API_KEY=test-api-key")
            .run(context -> {
                assertThat(context).hasSingleBean(StreamingChatModelClient.class);
                AiProperties properties = context.getBean(AiProperties.class);
                assertThat(properties.getProvider()).isEqualTo("qwen");
                assertThat(properties.getModel()).isEqualTo("qwen3.6-flash");
                assertThat(properties.resolveApiKey(context.getEnvironment())).isEqualTo("test-api-key");
            });
    }

    @Test
    void qwenApiKeyAliasIsSupportedWhenDashscopeKeyIsMissing() {
        contextRunner
            .withPropertyValues("QWEN_API_KEY=alias-api-key")
            .run(context -> {
                AiProperties properties = context.getBean(AiProperties.class);
                assertThat(properties.resolveApiKey(context.getEnvironment())).isEqualTo("alias-api-key");
            });
    }
}