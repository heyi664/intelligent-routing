package com.xinchan.voiceqa.asr;

import java.util.Map;

public record TencentAsrConfig(
    String secretId,
    String secretKey,
    String region,
    String engineModelType,
    String voiceFormat,
    int sampleRate,
    int timeoutMs
) {
    public static TencentAsrConfig fromEnvironment(Map<String, String> env) {
        return new TencentAsrConfig(
            env.getOrDefault("TENCENT_ASR_SECRET_ID", ""),
            env.getOrDefault("TENCENT_ASR_SECRET_KEY", ""),
            env.getOrDefault("TENCENT_ASR_REGION", "ap-guangzhou"),
            env.getOrDefault("TENCENT_ASR_ENGINE_MODEL_TYPE", "16k_zh"),
            env.getOrDefault("TENCENT_ASR_VOICE_FORMAT", "wav"),
            parseInt(env.get("TENCENT_ASR_SAMPLE_RATE"), 16000),
            parseInt(env.get("TENCENT_ASR_TIMEOUT_MS"), 5000)
        );
    }

    public TencentAsrConfig validate() {
        requireText(secretId, "TENCENT_ASR_SECRET_ID");
        requireText(secretKey, "TENCENT_ASR_SECRET_KEY");
        requireText(region, "TENCENT_ASR_REGION");
        requireText(engineModelType, "TENCENT_ASR_ENGINE_MODEL_TYPE");
        requireText(voiceFormat, "TENCENT_ASR_VOICE_FORMAT");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("TENCENT_ASR_SAMPLE_RATE must be positive");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("TENCENT_ASR_TIMEOUT_MS must be positive");
        }
        return this;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }
}