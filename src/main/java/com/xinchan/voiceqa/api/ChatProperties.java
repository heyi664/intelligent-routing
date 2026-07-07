package com.xinchan.voiceqa.api;

import com.xinchan.voiceqa.routing.RouteTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {
    public enum RouterProvider {
        RULE,
        QWEN
    }

    private String agentMode = "router";
    private RouteTarget manualAgent = RouteTarget.CLARIFICATION_AGENT;
    private RouterProvider routerProvider = RouterProvider.RULE;
    private double routeConfidenceThreshold = 0.60;

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

    public RouterProvider getRouterProvider() {
        return routerProvider;
    }

    public void setRouterProvider(RouterProvider routerProvider) {
        this.routerProvider = routerProvider;
    }

    public double getRouteConfidenceThreshold() {
        return routeConfidenceThreshold;
    }

    public void setRouteConfidenceThreshold(double routeConfidenceThreshold) {
        this.routeConfidenceThreshold = routeConfidenceThreshold;
    }
}
