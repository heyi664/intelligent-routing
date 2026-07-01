package com.xinchan.voiceqa.asr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(AsrProperties.class)
public class AsrConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "mock", matchIfMissing = true)
    public AsrClient mockAsrClient() {
        return new MockAsrClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public TencentAsrConfig tencentAsrConfig(AsrProperties properties, Environment environment) {
        return new TencentAsrConfig(
            firstText(properties.getSecretId(), environment.getProperty("TENCENT_ASR_SECRET_ID", "")),
            firstText(properties.getSecretKey(), environment.getProperty("TENCENT_ASR_SECRET_KEY", "")),
            firstText(properties.getRegion(), environment.getProperty("TENCENT_ASR_REGION", "ap-guangzhou")),
            firstText(properties.getEngineModelType(), environment.getProperty("TENCENT_ASR_ENGINE_MODEL_TYPE", "16k_zh")),
            firstText(properties.getVoiceFormat(), environment.getProperty("TENCENT_ASR_VOICE_FORMAT", "wav")),
            positiveOrDefault(properties.getSampleRate(), environment.getProperty("TENCENT_ASR_SAMPLE_RATE"), 16000),
            positiveOrDefault(properties.getTimeoutMs(), environment.getProperty("TENCENT_ASR_TIMEOUT_MS"), 5000)
        ).validate();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public TencentAsrSdkInvoker tencentAsrSdkInvoker() {
        return new TencentCloudJavaSdkInvoker();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public AsrClient tencentAsrClient(TencentAsrConfig config, TencentAsrSdkInvoker sdkInvoker) {
        return new TencentAsrSdkClient(config, sdkInvoker);
    }

    private static String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private static int positiveOrDefault(int preferred, String fallback, int defaultValue) {
        if (preferred > 0) {
            return preferred;
        }
        if (fallback == null || fallback.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(fallback);
    }
}