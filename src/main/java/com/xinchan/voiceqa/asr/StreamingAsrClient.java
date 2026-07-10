package com.xinchan.voiceqa.asr;

public interface StreamingAsrClient {
    StreamingAsrSession start(StreamingAsrRequest request, StreamingAsrListener listener);
}
