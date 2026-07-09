package com.xinchan.voiceqa.asr;

public record TencentAsrConfig(
    String secretId,
    String secretKey,
    String region,
    String engineModelType,
    String voiceFormat,
    int sampleRate,
    int timeoutMs
) {
    public TencentAsrConfig {
        secretId = sanitizeHeaderCredential(secretId);
        secretKey = sanitizeHeaderCredential(secretKey);
        region = trimText(region);
        engineModelType = trimText(engineModelType);
        voiceFormat = trimText(voiceFormat);
    }

    public TencentAsrConfig validate() {
        requireText(secretId, "app.asr.secret-id");
        requireText(secretKey, "app.asr.secret-key");
        requireText(region, "app.asr.region");
        requireText(engineModelType, "app.asr.engine-model-type");
        requireText(voiceFormat, "app.asr.voice-format");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("app.asr.sample-rate must be positive");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("app.asr.timeout-ms must be positive");
        }
        return this;
    }

    private static String sanitizeHeaderCredential(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "").strip();
    }

    private static String trimText(String value) {
        return value == null ? null : value.strip();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}