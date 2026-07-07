package com.xinchan.voiceqa.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinchan.voiceqa.ai.ChatModelRequest;
import com.xinchan.voiceqa.ai.StreamingChatModelClient;
import com.xinchan.voiceqa.api.ChatRequest;
import com.xinchan.voiceqa.conversation.ConversationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.chat", name = "router-provider", havingValue = "qwen")
public class QwenRouterAgent implements RouterAgent {
    private static final Logger log = LoggerFactory.getLogger(QwenRouterAgent.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModelClient modelClient;
    private final RouterPromptFactory promptFactory;

    public QwenRouterAgent(StreamingChatModelClient modelClient, RouterPromptFactory promptFactory) {
        this.modelClient = modelClient;
        this.promptFactory = promptFactory;
    }

    @Override
    public RouteCandidate classify(ChatRequest request, ConversationState state) {
        String content;
        try {
            log.info(
                "Qwen router request conversationId={} currentAgent={} message={}",
                request.conversationId(),
                state.currentAgent(),
                request.message()
            );
            content = modelClient.streamAsText(new ChatModelRequest(
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(request, state),
                request.conversationId(),
                request.conversationId()
            ));
            log.info("Qwen router raw response conversationId={} content={}", request.conversationId(), content);
        } catch (RuntimeException ex) {
            log.warn(
                "Qwen router fallback conversationId={} intent=ROUTER_MODEL_ERROR errorType={} errorMessage={}",
                request.conversationId(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
            );
            return invalidCandidate(request.message(), "ROUTER_MODEL_ERROR");
        }

        try {
            RouteCandidate candidate = parseCandidate(content);
            log.info(
                "Qwen router decision conversationId={} targetAgent={} intent={} confidence={} shouldStayInCurrentAgent={} reason={}",
                request.conversationId(),
                candidate.targetAgent(),
                candidate.intent(),
                candidate.confidence(),
                candidate.shouldStayInCurrentAgent(),
                candidate.reason()
            );
            return candidate;
        } catch (RuntimeException ex) {
            log.warn(
                "Qwen router fallback conversationId={} intent=ROUTER_OUTPUT_INVALID rawResponse={} errorType={} errorMessage={}",
                request.conversationId(),
                content,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
            );
            return invalidCandidate(request.message(), "ROUTER_OUTPUT_INVALID");
        }
    }

    private RouteCandidate parseCandidate(String content) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(extractJson(content));
            String rewrittenQuestion = requiredText(root, "rewrittenQuestion");
            String intent = requiredText(root, "intent");
            RouteTarget targetAgent = RouteTarget.valueOf(requiredText(root, "targetAgent"));
            boolean shouldStay = requiredBoolean(root, "shouldStayInCurrentAgent");
            double confidence = requiredDouble(root, "confidence");
            String reason = requiredText(root, "reason");
            return new RouteCandidate(rewrittenQuestion, intent, targetAgent, shouldStay, confidence, reason);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid router JSON", ex);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Missing router JSON content");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Missing router JSON object");
        }
        return content.substring(start, end + 1);
    }

    private RouteCandidate invalidCandidate(String message, String intent) {
        return new RouteCandidate(
            message == null ? "" : message,
            intent,
            RouteTarget.CLARIFICATION_AGENT,
            false,
            0.0,
            "invalid router output"
        );
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("Missing JSON text field: " + field);
        }
        return node.asText();
    }

    private boolean requiredBoolean(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException("Missing JSON boolean field: " + field);
        }
        return node.asBoolean();
    }

    private double requiredDouble(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("Missing JSON number field: " + field);
        }
        return node.asDouble();
    }
}