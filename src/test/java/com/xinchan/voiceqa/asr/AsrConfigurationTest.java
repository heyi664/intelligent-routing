package com.xinchan.voiceqa.asr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AsrConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AsrConfiguration.class));

    @Test
    void defaultProviderUsesMockAsrClient() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AsrClient.class);
            assertThat(context.getBean(AsrClient.class)).isInstanceOf(MockAsrClient.class);
        });
    }

    @Test
    void tencentProviderUsesTencentSdkClientAndEnvironmentCredentials() {
        contextRunner
            .withPropertyValues(
                "app.asr.provider=tencent",
                "TENCENT_ASR_SECRET_ID=test-secret-id",
                "TENCENT_ASR_SECRET_KEY=test-secret-key"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(AsrClient.class);
                assertThat(context.getBean(AsrClient.class)).isInstanceOf(TencentAsrSdkClient.class);
                assertThat(context.getBean(TencentAsrConfig.class).secretId()).isEqualTo("test-secret-id");
                assertThat(context.getBean(TencentAsrConfig.class).secretKey()).isEqualTo("test-secret-key");
            });
    }
}