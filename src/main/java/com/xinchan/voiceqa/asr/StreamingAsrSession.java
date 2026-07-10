package com.xinchan.voiceqa.asr;

public interface StreamingAsrSession extends AutoCloseable {
    void sendAudio(byte[] audioBytes);

    void stop();

    @Override
    void close();
}
