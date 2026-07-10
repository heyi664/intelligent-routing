package com.xinchan.voiceqa.asr;

public record TencentRealtimeAsrConfig(
    String appId,
    String secretId,
    String secretKey,
    String engineModelType,
    int timeoutMs,
    int vadSilenceTimeMs
) {
    public TencentRealtimeAsrConfig {
        appId = trim(appId);
        secretId = sanitize(secretId);
        secretKey = sanitize(secretKey);
        engineModelType = trim(engineModelType);
    }

    public TencentRealtimeAsrConfig validate() {
        requireText(appId, "app.asr.app-id");
        requireText(secretId, "app.asr.secret-id");
        requireText(secretKey, "app.asr.secret-key");
        requireText(engineModelType, "app.asr.engine-model-type");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("app.asr.timeout-ms must be positive");
        }
        if (vadSilenceTimeMs < 240 || vadSilenceTimeMs > 2000) {
            throw new IllegalArgumentException("app.asr.realtime-vad-silence-time-ms must be between 240 and 2000");
        }
        return this;
    }

    private static String sanitize(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "").strip();
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
