package com.xinchan.voiceqa.asr;

import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionRequest;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TencentCloudJavaSdkInvokerTest {

    @Test
    void buildsSentenceRecognitionRequestFromWavAudioBytesAndConfig() {
        AtomicReference<SentenceRecognitionRequest> capturedRequest = new AtomicReference<>();
        TencentCloudJavaSdkInvoker invoker = new TencentCloudJavaSdkInvoker((request, config) -> {
            capturedRequest.set(request);
            SentenceRecognitionResponse response = new SentenceRecognitionResponse();
            response.setResult("transcript");
            return response;
        });
        byte[] audioBytes = "fake-wav".getBytes(StandardCharsets.UTF_8);

        String result = invoker.recognize(audioBytes, new TencentAsrConfig(
            "secret-id",
            "secret-key",
            "ap-guangzhou",
            "16k_zh",
            "wav",
            16000,
            5000
        ));

        SentenceRecognitionRequest request = capturedRequest.get();
        assertThat(result).isEqualTo("transcript");
        assertThat(request.getEngSerViceType()).isEqualTo("16k_zh");
        assertThat(request.getSourceType()).isEqualTo(1L);
        assertThat(request.getVoiceFormat()).isEqualTo("wav");
        assertThat(request.getData()).isEqualTo(Base64.getEncoder().encodeToString(audioBytes));
        assertThat(request.getDataLen()).isEqualTo((long) audioBytes.length);
        assertThat(request.getInputSampleRate()).isNull();
        assertThat(request.getProjectId()).isEqualTo(0L);
        assertThat(request.getSubServiceType()).isEqualTo(2L);
        assertThat(request.getUsrAudioKey()).isNotBlank();
    }

    @Test
    void sendsInputSampleRateForPcmAudioBytes() {
        AtomicReference<SentenceRecognitionRequest> capturedRequest = new AtomicReference<>();
        TencentCloudJavaSdkInvoker invoker = new TencentCloudJavaSdkInvoker((request, config) -> {
            capturedRequest.set(request);
            SentenceRecognitionResponse response = new SentenceRecognitionResponse();
            response.setResult("transcript");
            return response;
        });

        invoker.recognize("fake-pcm".getBytes(StandardCharsets.UTF_8), new TencentAsrConfig(
            "secret-id",
            "secret-key",
            "ap-guangzhou",
            "16k_zh",
            "pcm",
            16000,
            5000
        ));

        SentenceRecognitionRequest request = capturedRequest.get();
        assertThat(request.getVoiceFormat()).isEqualTo("pcm");
        assertThat(request.getInputSampleRate()).isEqualTo(16000L);
    }
}