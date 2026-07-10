package com.xinchan.voiceqa.asr;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableConfigurationProperties(AsrProperties.class)
public class AsrConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "mock", matchIfMissing = true)
    public AsrClient mockAsrClient() {
        return new MockAsrClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "mock", matchIfMissing = true)
    public StreamingAsrClient mockStreamingAsrClient(AsrClient asrClient) {
        return new BufferedStreamingAsrClient(asrClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public TencentAsrConfig tencentAsrConfig(AsrProperties properties) {
        return new TencentAsrConfig(
            properties.getSecretId(),
            properties.getSecretKey(),
            properties.getRegion(),
            properties.getEngineModelType(),
            properties.getVoiceFormat(),
            properties.getSampleRate(),
            properties.getTimeoutMs()
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

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public TencentRealtimeAsrConfig tencentRealtimeAsrConfig(AsrProperties properties) {
        return new TencentRealtimeAsrConfig(
            properties.getAppId(),
            properties.getSecretId(),
            properties.getSecretKey(),
            properties.getEngineModelType(),
            properties.getRealtimeTimeoutMs(),
            properties.getRealtimeVadSilenceTimeMs()
        ).validate();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "tencent")
    public StreamingAsrClient tencentStreamingAsrClient(TencentRealtimeAsrConfig config) {
        return new TencentAsrRealtimeApiClient(config);
    }
}
