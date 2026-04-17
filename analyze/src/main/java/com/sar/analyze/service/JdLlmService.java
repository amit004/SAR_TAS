package com.sar.anaylze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sar.anaylze.dto.JdStructuredResponse;
import com.sar.anaylze.util.CommonUtils;
import com.sar.anaylze.util.SarUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Parses JD text into structured JSON using prompts; LLM HTTP is delegated to {@link LlmChatClient}.
 */
@ApplicationScoped
public class JdLlmService {

    private static final Logger LOG = Logger.getLogger(JdLlmService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    LlmChatClient llmChatClient;

    @ConfigProperty(name = "llm.prompt.max-chars", defaultValue = "12000")
    int llmPromptMaxChars;

    public JdStructuredResponse buildStructuredResponse(String jdText, String interviewRequirement) {
        if (!llmChatClient.isConfigured()) {
            return SarUtils.fallbackJdStructured(jdText, interviewRequirement);
        }
        try {
            int cap = Math.max(2000, llmPromptMaxChars);
            String jd = CommonUtils.truncate(jdText == null ? "" : jdText, cap);
            String req = CommonUtils.truncate(interviewRequirement == null ? "" : interviewRequirement, cap);
            String prompt = buildJdPrompt(jd, req);
            String content = llmChatClient.completeUserMessage(prompt);
            if (content == null || content.isBlank()) {
                return SarUtils.fallbackJdStructured(jdText, interviewRequirement);
            }
            return parseLlmJson(content, jd);
        } catch (Exception ex) {
            LOG.warnf(ex, "LLM call failed; returning fallback");
            return SarUtils.fallbackJdStructured(jdText, interviewRequirement);
        }
    }

    private String buildJdPrompt(String jdText, String interviewRequirement) {
        return """
                Convert the job description and interview requirement into a single JSON object only (no markdown, no commentary).
                Be concise: short strings, skills list at most 20 items.
                Use these exact keys (snake_case) so the output parses correctly:
                _id, name, skills_required_per_experience_level, searchable_text, role_and_responsibilities,
                experience_reqd, total_industry_years_of_experience, max_compensation, location, mode, createAt,
                working_hours, notice_period, employment_type, number_of_positions, notes

                Rules:
                - skills_required_per_experience_level must be a JSON array of strings (e.g. ["Java 3+ years"]).
                - Use empty string "" for any missing scalar field.
                - searchable_text should be a short summary for search if not obvious from the JD.

                Interview requirement:
                %s

                Job description text:
                %s
                """.formatted(
                interviewRequirement == null ? "" : interviewRequirement,
                jdText == null ? "" : jdText);
    }

    private JdStructuredResponse parseLlmJson(String llmContent, String jdText) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(CommonUtils.sanitizeJson(llmContent));
        return new JdStructuredResponse(
                CommonUtils.readText(root, "_id", UUID.randomUUID().toString()),
                CommonUtils.readText(root, "name", ""),
                CommonUtils.readStringArray(root, "skills_required_per_experience_level"),
                CommonUtils.readText(root, "searchable_text", CommonUtils.searchableTextPreview(jdText)),
                CommonUtils.readText(root, "role_and_responsibilities", ""),
                CommonUtils.readText(root, "experience_reqd", ""),
                CommonUtils.readText(root, "total_industry_years_of_experience", ""),
                CommonUtils.readText(root, "max_compensation", ""),
                CommonUtils.readText(root, "location", ""),
                CommonUtils.readText(root, "mode", ""),
                CommonUtils.readText(root, "createAt", LocalDateTime.now().toString()),
                CommonUtils.readText(root, "working_hours", ""),
                CommonUtils.readText(root, "notice_period", ""),
                CommonUtils.readText(root, "employment_type", ""),
                CommonUtils.readText(root, "number_of_positions", ""),
                CommonUtils.readText(root, "notes", ""));
    }
}
