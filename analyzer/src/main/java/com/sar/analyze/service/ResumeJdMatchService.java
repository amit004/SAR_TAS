package com.sar.anaylze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sar.anaylze.dto.CandidateJdMatchResponse;
import com.sar.anaylze.dto.JdStructuredResponse;
import com.sar.anaylze.util.CommonUtils;
import com.sar.anaylze.util.SarUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ResumeJdMatchService {

    private static final Logger LOG = Logger.getLogger(ResumeJdMatchService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    LlmChatClient llmChatClient;

    /** Characters kept from resume text for scoring (JD side is compact JSON). */
    @ConfigProperty(name = "llm.match.max-chars-resume", defaultValue = "6000")
    int matchMaxCharsResume;

    /** Cap serialized structured JD JSON in the prompt (safety). */
    @ConfigProperty(name = "llm.match.max-chars-jd-json", defaultValue = "8000")
    int matchMaxCharsJdJson;

    /** Output token cap for scoring JSON. */
    @ConfigProperty(name = "llm.match.max-tokens", defaultValue = "896")
    int matchMaxTokens;

    /**
     * Scores resume against an already-parsed structured JD (compact JSON), not raw JD text.
     */
    public CandidateJdMatchResponse scoreResumeAgainstStructuredJd(String resumeText, JdStructuredResponse jdStructured) {
        int resumeCap = Math.max(2000, matchMaxCharsResume);
        String resume = CommonUtils.truncate(resumeText == null ? "" : resumeText, resumeCap);
        String jdJson = compactStructuredJdJson(jdStructured);
        String prompt = buildScoringPromptFromStructured(jdJson, resume);
        int outCap = Math.max(256, matchMaxTokens);
        String raw = llmChatClient.completeUserMessage(prompt, outCap);
        if (raw == null || raw.isBlank()) {
            return SarUtils.fallbackCandidateMatch("LLM not configured or call failed.");
        }
        try {
            return parseScoreJson(raw);
        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to parse match score JSON");
            return SarUtils.fallbackCandidateMatch("Could not parse LLM response.");
        }
    }

    private String compactStructuredJdJson(JdStructuredResponse jd) {
        if (jd == null) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(jd);
            int cap = Math.max(500, matchMaxCharsJdJson);
            if (json.length() <= cap) {
                return json;
            }
            return json.substring(0, cap) + "\n… [truncated]";
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Could not serialize JD profile to JSON");
            return "{}";
        }
    }

    private String buildScoringPromptFromStructured(String jobProfileJson, String resume) {
        return """
                You compare a candidate RESUME to a structured JOB PROFILE (JSON). The profile was extracted from the job description.
                Output one JSON object only, no markdown.
                Keys: overall_score (0-100 int), summary (2 sentences max), strengths (max 5 short strings), gaps (max 5 short strings), recommendation (5 words max).

                JOB_PROFILE_JSON:
                %s

                RESUME:
                %s
                """.formatted(jobProfileJson, resume);
    }

    private CandidateJdMatchResponse parseScoreJson(String llmContent) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(CommonUtils.sanitizeJson(llmContent));
        int score = CommonUtils.readIntClamped(root, "overall_score", 0, 100);
        return new CandidateJdMatchResponse(
                score,
                CommonUtils.readText(root, "summary", ""),
                CommonUtils.readStringArray(root, "strengths"),
                CommonUtils.readStringArray(root, "gaps"),
                CommonUtils.readText(root, "recommendation", ""));
    }
}
