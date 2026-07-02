package com.xinchan.voiceqa.api;

import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {
    private String agentMode = "router";
    private RouteTarget manualAgent = RouteTarget.CLARIFICATION_AGENT;

    public static ChatProperties router() {
        return new ChatProperties();
    }

    public static ChatProperties manual(RouteTarget manualAgent) {
        ChatProperties properties = new ChatProperties();
        properties.setAgentMode("manual");
        properties.setManualAgent(manualAgent);
        return properties;
    }

    public boolean manualMode() {
        return "manual".equalsIgnoreCase(agentMode);
    }

    public String getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(String agentMode) {
        this.agentMode = agentMode;
    }

    public RouteTarget getManualAgent() {
        return manualAgent;
    }

    public void setManualAgent(RouteTarget manualAgent) {
        this.manualAgent = manualAgent;
    }
}
