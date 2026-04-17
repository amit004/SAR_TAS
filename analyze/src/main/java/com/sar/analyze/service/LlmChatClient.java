package com.sar.anaylze.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * OpenAI-compatible chat HTTP client (Ollama {@code /v1/chat/completions}, OpenAI, etc.).
 * Other services use this for all LLM round-trips; they do not perform HTTP themselves.
 */
@ApplicationScoped
public class LlmChatClient {

    private static final Logger LOG = Logger.getLogger(LlmChatClient.class);

    @ConfigProperty(name = "llm.api.url")
    String llmApiUrl;

    @ConfigProperty(name = "llm.api.model")
    String llmApiModel;

    @ConfigProperty(name = "llm.api.key")
    String llmApiKey;

    @ConfigProperty(name = "llm.api.connect-timeout.seconds", defaultValue = "10")
    int llmConnectTimeoutSeconds;

    @ConfigProperty(name = "llm.api.request-timeout.seconds", defaultValue = "600")
    int llmRequestTimeoutSeconds;

    /** Caps completion length (smaller = faster; too low may truncate JSON). OpenAI/Ollama: max_tokens. */
    @ConfigProperty(name = "llm.api.max-tokens", defaultValue = "3072")
    int llmMaxTokens;

    /** Lower = faster, more deterministic JSON (typical 0.1–0.5 for extraction). */
    @ConfigProperty(name = "llm.api.temperature", defaultValue = "0.25")
    double llmTemperature;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    @PostConstruct
    void initHttpClient() {
        int connectSec = Math.max(1, llmConnectTimeoutSeconds);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectSec))
                .build();
    }

    public boolean isConfigured() {
        return llmApiKey != null && !llmApiKey.isBlank() && !"YOUR_API_KEY".equals(llmApiKey);
    }

    /**
     * Single user message → assistant text. {@code null} if not configured or the call fails.
     * Uses {@link #llmMaxTokens} global cap.
     */
    public String completeUserMessage(String userContent) {
        return completeUserMessage(userContent, llmMaxTokens);
    }

    /**
     * Same as {@link #completeUserMessage(String)} but overrides {@code max_tokens} for this call
     * (e.g. resume/JD scoring only needs a short JSON reply — use a low value for faster completion).
     */
    public String completeUserMessage(String userContent, int maxTokens) {
        if (!isConfigured()) {
            return null;
        }
        try {
            ObjectNode payload = buildChatPayload(userContent, maxTokens);
            return sendChatCompletion(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            LOG.warnf(ex, "LLM chat completion failed");
            return null;
        }
    }

    private ObjectNode buildChatPayload(String userContent, int maxTokens) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", llmApiModel);
        payload.put("stream", false);
        int cap = Math.max(128, maxTokens);
        payload.put("max_tokens", cap);
        payload.put("temperature", Math.max(0.0, Math.min(2.0, llmTemperature)));
        payload.set("messages", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("role", "user")
                        .put("content", userContent)));
        return payload;
    }

    private String sendChatCompletion(String requestBody) throws IOException, InterruptedException {
        int requestSec = Math.max(5, llmRequestTimeoutSeconds);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmApiUrl))
                .timeout(Duration.ofSeconds(requestSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("LLM request timed out after " + requestSec + "s (Ollama may be loading the model or overloaded). "
                    + "Raise llm.api.request-timeout.seconds or check ollama is running.", e);
        }
        String body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = body == null ? "" : (body.length() > 500 ? body.substring(0, 500) + "…" : body);
            throw new IOException("LLM call failed with HTTP " + response.statusCode() + ": " + snippet);
        }

        JsonNode root = objectMapper.readTree(body);
        String content = extractAssistantContent(root);
        if (content == null || content.isBlank()) {
            String snippet = body == null ? "" : (body.length() > 800 ? body.substring(0, 800) + "…" : body);
            throw new IOException("LLM response missing assistant content. Body: " + snippet);
        }
        return content;
    }

    private String extractAssistantContent(JsonNode root) {
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).path("message");
        JsonNode contentNode = message.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }
            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : contentNode) {
                    if (part.has("text")) {
                        sb.append(part.path("text").asText(""));
                    } else {
                        sb.append(part.asText(""));
                    }
                }
                return sb.toString();
            }
        }
        return null;
    }
}
