package com.xinchan.voiceqa.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private String provider = "qwen";
    private String model = "qwen3.6-flash";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private int timeoutMs = 6000;
    private int connectTimeoutMs = 3000;
    private int routerTimeoutMs = 6000;
    private int agentTimeoutMs = 30000;
    private int routerMaxRetries = 1;
    private int agentMaxRetries = 1;
    private long retryBackoffMs = 200;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = sanitizeHeaderCredential(apiKey);
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRouterTimeoutMs() {
        return routerTimeoutMs;
    }

    public void setRouterTimeoutMs(int routerTimeoutMs) {
        this.routerTimeoutMs = routerTimeoutMs;
    }

    public int getAgentTimeoutMs() {
        return agentTimeoutMs;
    }

    public void setAgentTimeoutMs(int agentTimeoutMs) {
        this.agentTimeoutMs = agentTimeoutMs;
    }

    public int getRouterMaxRetries() {
        return routerMaxRetries;
    }

    public void setRouterMaxRetries(int routerMaxRetries) {
        this.routerMaxRetries = routerMaxRetries;
    }

    public int getAgentMaxRetries() {
        return agentMaxRetries;
    }

    public void setAgentMaxRetries(int agentMaxRetries) {
        this.agentMaxRetries = agentMaxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    private static String sanitizeHeaderCredential(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "").strip();
    }
}
