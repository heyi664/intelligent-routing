package com.xinchan.voiceqa;

import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.api.ChatResponse;
import com.xinchan.voiceqa.routing.RouteTarget;
import com.xinchan.voiceqa.routing.RouterService;
import com.xinchan.voiceqa.voice.VoiceChatRequest;
import com.xinchan.voiceqa.voice.VoicePipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DemoSpringContextTest {
    @Autowired
    private RouterService routerService;

    @Autowired
    private VoicePipelineService voicePipelineService;

    @Test
    void springContextWiresRouterAndVoicePipelineBeans() {
        ChatResponse textResponse = routerService.route(new ChatRequest(
            "spring-text-1",
            "u-1",
            "天然气缴费怎么操作？"
        ));
        ChatResponse voiceResponse = voicePipelineService.handle(new VoiceChatRequest(
            "spring-voice-1",
            "spring-voice-c-1",
            "u-1",
            new byte[] {1, 2, 3}
        ));

        assertEquals(RouteTarget.QA_AGENT, textResponse.targetAgent());
        assertEquals("QA", textResponse.source());
        assertEquals(RouteTarget.QA_AGENT, voiceResponse.targetAgent());
        assertEquals("QA", voiceResponse.source());
    }
}
