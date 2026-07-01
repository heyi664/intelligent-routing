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
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEngineModelType() {
        return engineModelType;
    }

    public void setEngineModelType(String engineModelType) {
        this.engineModelType = engineModelType;
    }

    public String getVoiceFormat() {
        return voiceFormat;
    }

    public void setVoiceFormat(String voiceFormat) {
        this.voiceFormat = voiceFormat;
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
}