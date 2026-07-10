package com.xinchan.voiceqa.asr;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TencentAsrRealtimeApiClientTest {
    @Test
    void buildsOfficialRealtimeAsrWebSocketSignature() {
        TencentRealtimeAsrConfig config = new TencentRealtimeAsrConfig(
            "1234567890",
            "testSecretId",
            "testSecretKey",
            "16k_zh",
            15000,
            1000
        );

        URI uri = TencentAsrRealtimeApiClient.buildSignedUri(config, "voice-test-1", 1_700_000_000L, 123456);

        assertThat(uri.toASCIIString()).isEqualTo(
            "wss://asr.cloud.tencent.com/asr/v2/1234567890?"
                + "convert_num_mode=1&engine_model_type=16k_zh&expired=1700086400"
                + "&filter_dirty=0&filter_empty_result=1&filter_modal=0&filter_punc=0"
                + "&needvad=1&nonce=123456&secretid=testSecretId&timestamp=1700000000"
                + "&vad_silence_time=1000&voice_format=1&voice_id=voice-test-1&word_info=0"
                + "&signature=C7TpiC7qWNzuzvUIt49w7%2BML6C8%3D"
        );
    }

    @Test
    void rejectsMissingAppIdBeforeOpeningRealtimeConnection() {
        TencentRealtimeAsrConfig config = new TencentRealtimeAsrConfig(
            " ",
            "testSecretId",
            "testSecretKey",
            "16k_zh",
            15000,
            1000
        );

        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("app.asr.app-id");
    }

    @Test
    void removesLineBreaksFromRealtimeCredentials() {
        TencentRealtimeAsrConfig config = new TencentRealtimeAsrConfig(
            "1234567890",
            "test\nSecretId",
            "testSecret\r\nKey",
            "16k_zh",
            15000,
            1000
        ).validate();

        assertThat(config.secretId()).isEqualTo("testSecretId");
        assertThat(config.secretKey()).isEqualTo("testSecretKey");
    }
}
