package com.xinchan.voiceqa.voice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeVoiceWebSocketConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void registersRealtimeVoiceWebSocketHandler() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RealtimeVoiceWebSocketHandler.class));
    }


    @Test
    void configuresWebSocketMessageBuffersForAudioBase64Frames() {
        RealtimeVoiceWebSocketConfig config = new RealtimeVoiceWebSocketConfig(null);

        ServletServerContainerFactoryBean container = config.webSocketContainer();

        assertThat(container.getMaxTextMessageBufferSize()).isGreaterThanOrEqualTo(1024 * 1024);
        assertThat(container.getMaxBinaryMessageBufferSize()).isGreaterThanOrEqualTo(1024 * 1024);
    }
    @Import({RealtimeVoiceWebSocketConfig.class, RealtimeVoiceWebSocketHandler.class})
    static class TestConfig {
        @Bean
        RealtimeVoiceSessionService realtimeVoiceSessionService() {
            return new RealtimeVoiceSessionService(event -> List.of(), null);
        }
    }
}
