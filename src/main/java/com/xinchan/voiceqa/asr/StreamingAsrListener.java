package com.xinchan.voiceqa.asr;

public interface StreamingAsrListener {
    void onResult(StreamingAsrResult result);

    void onComplete();

    void onError(Throwable error);
}
