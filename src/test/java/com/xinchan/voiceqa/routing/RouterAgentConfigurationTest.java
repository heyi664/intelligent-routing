package com.xinchan.voiceqa.routing;

import com.xinchan.voiceqa.api.ChatProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterAgentConfigurationTest {

    @Test
    void defaultsToRuleRouterAndSixtyPercentThreshold() {
        ChatProperties properties = new ChatProperties();

        assertEquals(ChatProperties.RouterProvider.RULE, properties.getRouterProvider());
        assertEquals(0.60, properties.getRouteConfidenceThreshold(), 0.001);
    }
}
