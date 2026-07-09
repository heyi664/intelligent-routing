package com.xinchan.voiceqa.voice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.memory.enabled=false"
)
class VoiceTestPageStaticResourceTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesVoiceTestPageWithRealtimeVoiceControls() {
        ResponseEntity<String> response = restTemplate.getForEntity("/voice-test.html", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ws://", "/api/voice/realtime", "startButton", "stopButton");
        assertThat(response.getBody()).contains("AudioContext", "createWavBlob", "TARGET_SAMPLE_RATE", "audioFormat", "sampleRate");
        assertThat(response.getBody()).contains("traceId", "createTraceId", "currentTraceId = createTraceId()", "chat_start", "chat_delta", "chat_done");
        assertThat(response.getBody()).doesNotContain("new MediaRecorder", "`n");
    }
}
