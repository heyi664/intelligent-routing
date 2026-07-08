package com.xinchan.voiceqa.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory")
public class MemoryProperties {
    private boolean enabled = false;
    private int recentTurnLimit = 8;
    private int summaryTriggerTurns = 12;
    private int summaryMaxChars = 800;
    private long redisTtlSeconds = 86400;
    private String redisHost = "192.168.23.129";
    private int redisPort = 6379;
    private String redisPassword = "";
    private int redisTimeoutMs = 2000;
    private String jdbcUrl = "jdbc:postgresql://192.168.23.129:5432/intelligent-routing";
    private String jdbcUsername = "postgres";
    private String jdbcPassword = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRecentTurnLimit() { return recentTurnLimit; }
    public void setRecentTurnLimit(int recentTurnLimit) { this.recentTurnLimit = recentTurnLimit; }
    public int getSummaryTriggerTurns() { return summaryTriggerTurns; }
    public void setSummaryTriggerTurns(int summaryTriggerTurns) { this.summaryTriggerTurns = summaryTriggerTurns; }
    public int getSummaryMaxChars() { return summaryMaxChars; }
    public void setSummaryMaxChars(int summaryMaxChars) { this.summaryMaxChars = summaryMaxChars; }
    public long getRedisTtlSeconds() { return redisTtlSeconds; }
    public void setRedisTtlSeconds(long redisTtlSeconds) { this.redisTtlSeconds = redisTtlSeconds; }
    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }
    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int redisPort) { this.redisPort = redisPort; }
    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }
    public int getRedisTimeoutMs() { return redisTimeoutMs; }
    public void setRedisTimeoutMs(int redisTimeoutMs) { this.redisTimeoutMs = redisTimeoutMs; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getJdbcUsername() { return jdbcUsername; }
    public void setJdbcUsername(String jdbcUsername) { this.jdbcUsername = jdbcUsername; }
    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }
}
