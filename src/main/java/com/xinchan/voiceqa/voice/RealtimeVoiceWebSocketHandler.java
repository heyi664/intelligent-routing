package com.xinchan.voiceqa.voice;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class RealtimeVoiceWebSocketHandler extends TextWebSocketHandler {
    private final RealtimeVoiceSessionService realtimeVoiceSessionService;

    public RealtimeVoiceWebSocketHandler(RealtimeVoiceSessionService realtimeVoiceSessionService) {
        this.realtimeVoiceSessionService = realtimeVoiceSessionService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        realtimeVoiceSessionService.handleText(session.getId(), message.getPayload(), outbound -> send(session, outbound));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeVoiceSessionService.close(session.getId());
    }

    private void send(WebSocketSession session, String payload) {
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to send realtime voice websocket message", ex);
        }
    }
}
