package com.xinchan.voiceqa.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.asr")
public class AsrProperties {
    private String provider = "mock";
    private String secretId;
    private String secretKey;
    private String region = "ap-guangzhou";
    private String engineModelType = "16k_zh";
    private String voiceFormat = "wav";
    private int sampleRate = 16000;
    private int timeoutMs = 5000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = sanitizeHeaderCredential(secretId);
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = sanitizeHeaderCredential(secretKey);
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = trimText(region);
    }

    public String getEngineModelType() {
        return engineModelType;
    }

    public void setEngineModelType(String engineModelType) {
        this.engineModelType = trimText(engineModelType);
    }

    public String getVoiceFormat() {
        return voiceFormat;
    }

    public void setVoiceFormat(String voiceFormat) {
        this.voiceFormat = trimText(voiceFormat);
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    private static String sanitizeHeaderCredential(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "").strip();
    }

    private static String trimText(String value) {
        return value == null ? null : value.strip();
    }
}