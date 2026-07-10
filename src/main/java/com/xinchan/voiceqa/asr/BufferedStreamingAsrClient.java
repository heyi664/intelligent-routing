package com.xinchan.voiceqa.asr;

import com.xinchan.voiceqa.voice.VoiceInputEvent;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BufferedStreamingAsrClient implements StreamingAsrClient {
    private final AsrClient asrClient;

    public BufferedStreamingAsrClient(AsrClient asrClient) {
        this.asrClient = asrClient;
    }

    @Override
    public StreamingAsrSession start(StreamingAsrRequest request, StreamingAsrListener listener) {
        return new StreamingAsrSession() {
            private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
            private final AtomicBoolean closed = new AtomicBoolean();

            @Override
            public void sendAudio(byte[] audioBytes) {
                requireOpen();
                audio.writeBytes(audioBytes);
            }

            @Override
            public void stop() {
                requireOpen();
                try {
                    List<AsrResult> results = asrClient.transcribe(new VoiceInputEvent(
                        request.voiceSessionId(),
                        request.conversationId(),
                        request.userId(),
                        audio.toByteArray(),
                        "",
                        request.audioFormat(),
                        request.sampleRate()
                    ));
                    int index = 0;
                    for (AsrResult result : results) {
                        listener.onResult(new StreamingAsrResult(
                            result.transcript(),
                            result.stable(),
                            index++,
                            result.offsetMs(),
                            result.offsetMs()
                        ));
                    }
                    listener.onComplete();
                } catch (RuntimeException ex) {
                    listener.onError(ex);
                } finally {
                    close();
                }
            }

            @Override
            public void close() {
                closed.set(true);
            }

            private void requireOpen() {
                if (closed.get()) {
                    throw new IllegalStateException("Streaming ASR session is closed");
                }
            }
        };
    }
}
