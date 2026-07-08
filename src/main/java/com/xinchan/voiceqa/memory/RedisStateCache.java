package com.xinchan.voiceqa.memory;

import com.xinchan.voiceqa.conversation.ConversationState;
import com.xinchan.voiceqa.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "true")
public class RedisStateCache {
    private static final Logger log = LoggerFactory.getLogger(RedisStateCache.class);

    private final MemoryProperties properties;

    public RedisStateCache(MemoryProperties properties) {
        this.properties = properties;
    }

    public Optional<ConversationState> get(String conversationId) {
        try {
            String value = command("GET", key(conversationId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            String[] parts = value.split("\\n", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            return Optional.of(new ConversationState(
                parts[0],
                parts[1],
                RouteTarget.valueOf(parts[2]),
                Instant.parse(parts[3])
            ));
        } catch (RuntimeException ex) {
            log.warn("Redis state read failed conversationId={} errorType={} errorMessage={}", conversationId, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    public void set(ConversationState state) {
        try {
            String value = String.join("\n", state.conversationId(), state.userId(), state.currentAgent().name(), state.updatedAt().toString());
            command("SETEX", key(state.conversationId()), String.valueOf(properties.getRedisTtlSeconds()), value);
        } catch (RuntimeException ex) {
            log.warn("Redis state write failed conversationId={} errorType={} errorMessage={}", state.conversationId(), ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private String key(String conversationId) {
        return "voiceqa:conversation:state:" + conversationId;
    }

    private String command(String... args) {
        try (Socket socket = new Socket(properties.getRedisHost(), properties.getRedisPort())) {
            socket.setSoTimeout(properties.getRedisTimeoutMs());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            authenticateIfNeeded(writer, reader);
            writeCommand(writer, args);
            return readResponse(reader);
        } catch (Exception ex) {
            throw new IllegalStateException("Redis command failed", ex);
        }
    }

    private void authenticateIfNeeded(BufferedWriter writer, BufferedReader reader) throws IOException {
        String password = properties.getRedisPassword();
        if (password == null || password.isBlank()) {
            return;
        }
        writeCommand(writer, "AUTH", password);
        readResponse(reader);
    }

    private void writeCommand(BufferedWriter writer, String... args) throws IOException {
        writer.write("*" + args.length + "\r\n");
        for (String arg : args) {
            writer.write("$" + arg.getBytes(StandardCharsets.UTF_8).length + "\r\n");
            writer.write(arg + "\r\n");
        }
        writer.flush();
    }

    private String readResponse(BufferedReader reader) throws IOException {
        String first = reader.readLine();
        if (first == null) {
            return null;
        }
        if (first.startsWith("+")) {
            return first.substring(1);
        }
        if (first.startsWith("$")) {
            int length = Integer.parseInt(first.substring(1));
            if (length < 0) {
                return null;
            }
            char[] chars = new char[length];
            int read = reader.read(chars, 0, length);
            reader.readLine();
            return read < 0 ? null : new String(chars, 0, read);
        }
        if (first.startsWith(":")) {
            return first.substring(1);
        }
        if (first.startsWith("-")) {
            throw new IllegalStateException(first.substring(1));
        }
        return first;
    }
}
