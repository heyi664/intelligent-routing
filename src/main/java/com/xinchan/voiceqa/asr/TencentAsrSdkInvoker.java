package com.xinchan.voiceqa.asr;

public interface TencentAsrSdkInvoker {
    String recognize(byte[] audioBytes, TencentAsrConfig config);
}