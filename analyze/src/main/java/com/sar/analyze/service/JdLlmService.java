package com.sar.anaylze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sar.anaylze.dto.JdStructuredResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JdLlmService {

    @ConfigProperty(name = "llm.api.url")
    String llmApiUrl;

    @ConfigProperty(name = "llm.api.model")
    String llmApiModel;

    @ConfigProperty(name = "llm.api.key")
    String llmApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdStructuredResponse buildStructuredResponse(String jdText, String interviewRequirement) {
        if (llmApiKey == null || llmApiKey.isBlank() || "YOUR_API_KEY".equals(llmApiKey)) {
            return fallbackResponse(jdText, interviewRequirement);
        }

        try {
            String requestBody = buildChatRequest(jdText, interviewRequirement);
            String content = callLlm(requestBody);
            return parseLlmJson(content, jdText);
        } catch (Exception ex) {
            return fallbackResponse(jdText, interviewRequirement);
        }
    }

    private String callLlm(String requestBody) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LLM call failed with status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IOException("LLM response missing content");
        }
        return contentNode.asText();
    }

    private String buildChatRequest(String jdText, String interviewRequirement) throws JsonProcessingException {
        String prompt = """
                Convert the given job description and interview requirement into STRICT JSON.
                Return only JSON object with the exact keys:
                _id
                Name
                Skill Required year wise or level []
                searchable_text
                Role and Responsibilities
                Experience Re
                Total industry Years of Experience
                Max Compensation
                Location
                Mode
                createAt
                Working hours
                Notice Period
                Employment Type
                Number of Positions
                Notes : important notes

                If a value is missing, use empty string. For skill list, return array of strings.

                Interview Requirement:
                %s

                Job Description Text:
                %s
                """.formatted(interviewRequirement, jdText);

        JsonNode payload = objectMapper.createObjectNode()
                .put("model", llmApiModel)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        return objectMapper.writeValueAsString(payload);
    }

    private JdStructuredResponse parseLlmJson(String llmContent, String jdText) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(sanitizeJson(llmContent));
        return new JdStructuredResponse(
                readText(root, "_id", UUID.randomUUID().toString()),
                readText(root, "name", ""),
                readArray(root, "skills_required_per_experience_level"),
                readText(root, "searchable_text", defaultSearchableText(jdText)),
                readText(root, "role_and_responsibilities", ""),
                readText(root, "experience_reqd", ""),
                readText(root, "total_industry_years_of_experience", ""),
                readText(root, "max_compensation", ""),
                readText(root, "location", ""),
                readText(root, "mode", ""),
                readText(root, "createAt", LocalDateTime.now().toString()),
                readText(root, "working_hours", ""),
                readText(root, "notice_period", ""),
                readText(root, "employment_type", ""),
                readText(root, "number_of_positions", ""),
                readText(root, "notes", "")
        );
    }

    private JdStructuredResponse fallbackResponse(String jdText, String interviewRequirement) {
        return new JdStructuredResponse(
                UUID.randomUUID().toString(),
                "Unknown",
                List.of(),
                defaultSearchableText(jdText),
                "",
                interviewRequirement == null ? "" : interviewRequirement,
                "",
                "",
                "",
                "",
                LocalDateTime.now().toString(),
                "",
                "",
                "",
                "",
                "LLM not configured or unavailable. Returning fallback response."
        );
    }

    private String sanitizeJson(String content) {
        String sanitized = content.trim();
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }
        return sanitized;
    }

    private String readText(JsonNode node, String key, String defaultValue) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    private List<String> readArray(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return value.findValuesAsText("");
    }

    private String defaultSearchableText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }
}
