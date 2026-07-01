package com.xinchan.voiceqa.asr;

import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionRequest;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;

import java.util.Base64;
import java.util.UUID;

public class TencentCloudJavaSdkInvoker implements TencentAsrSdkInvoker {
    private static final String ASR_ENDPOINT = "asr.tencentcloudapi.com";
    private final SentenceRecognitionCall sentenceRecognitionCall;

    public TencentCloudJavaSdkInvoker() {
        this(TencentCloudJavaSdkInvoker::callTencentCloud);
    }

    TencentCloudJavaSdkInvoker(SentenceRecognitionCall sentenceRecognitionCall) {
        this.sentenceRecognitionCall = sentenceRecognitionCall;
    }

    @Override
    public String recognize(byte[] audioBytes, TencentAsrConfig config) {
        SentenceRecognitionRequest request = buildRequest(audioBytes, config.validate());
        try {
            SentenceRecognitionResponse response = sentenceRecognitionCall.recognize(request, config);
            if (response == null || response.getResult() == null || response.getResult().isBlank()) {
                throw new IllegalStateException("Tencent ASR SDK returned empty result");
            }
            return response.getResult();
        } catch (TencentCloudSDKException e) {
            throw new IllegalStateException("Tencent ASR SDK invocation failed: " + e.getMessage(), e);
        }
    }

    private static SentenceRecognitionRequest buildRequest(byte[] audioBytes, TencentAsrConfig config) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Voice audio bytes are required for Tencent ASR SDK");
        }

        SentenceRecognitionRequest request = new SentenceRecognitionRequest();
        request.setProjectId(0L);
        request.setSubServiceType(2L);
        request.setEngSerViceType(config.engineModelType());
        request.setSourceType(1L);
        request.setVoiceFormat(config.voiceFormat());
        request.setUsrAudioKey(UUID.randomUUID().toString());
        request.setData(Base64.getEncoder().encodeToString(audioBytes));
        request.setDataLen((long) audioBytes.length);
        request.setInputSampleRate((long) config.sampleRate());
        return request;
    }

    private static SentenceRecognitionResponse callTencentCloud(
        SentenceRecognitionRequest request,
        TencentAsrConfig config
    ) throws TencentCloudSDKException {
        Credential credential = new Credential(config.secretId(), config.secretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(ASR_ENDPOINT);
        httpProfile.setConnTimeout(config.timeoutMs());
        httpProfile.setReadTimeout(config.timeoutMs());
        httpProfile.setWriteTimeout(config.timeoutMs());

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        com.tencentcloudapi.asr.v20190614.AsrClient client =
            new com.tencentcloudapi.asr.v20190614.AsrClient(credential, config.region(), clientProfile);
        return client.SentenceRecognition(request);
    }

    @FunctionalInterface
    interface SentenceRecognitionCall {
        SentenceRecognitionResponse recognize(
            SentenceRecognitionRequest request,
            TencentAsrConfig config
        ) throws TencentCloudSDKException;
    }
}