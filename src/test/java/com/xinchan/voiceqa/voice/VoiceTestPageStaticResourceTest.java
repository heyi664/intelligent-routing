package com.xinchan.voiceqa.voice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.memory.enabled=false", "app.asr.provider=mock"}
)
class VoiceTestPageStaticResourceTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesVoiceTestPageWithRealtimeVoiceControls() {
        ResponseEntity<String> response = restTemplate.getForEntity("/voice-test.html", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ws://", "/api/voice/realtime", "startButton", "stopButton");
        assertThat(response.getBody()).contains(
            "AudioContext",
            "encodePcm16",
            "resampleLinear",
            "TARGET_SAMPLE_RATE",
            "audioFormat",
            "sampleRate",
            "waitForServerStarted(20000)",
            "await serverStarted",
            "socket.send(pcm)",
            "asr_partial"
        );
        assertThat(response.getBody()).contains("traceId", "createTraceId", "currentTraceId = createTraceId()", "chat_start", "chat_delta", "chat_done");
        assertThat(response.getBody()).doesNotContain("createWavBlob", "audioBase64", "new MediaRecorder", "`n");
    }
}
