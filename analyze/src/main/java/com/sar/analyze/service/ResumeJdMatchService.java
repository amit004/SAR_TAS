package com.sar.anaylze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sar.anaylze.dto.CandidateJdMatchResponse;
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

    /** Characters kept per PDF for /api/match/score (lower = faster). */
    @ConfigProperty(name = "llm.match.max-chars-per-doc", defaultValue = "5000")
    int matchMaxCharsPerDoc;

    /** Output token cap for scoring JSON only — keep low for speed (raise if JSON is truncated). */
    @ConfigProperty(name = "llm.match.max-tokens", defaultValue = "896")
    int matchMaxTokens;

    public CandidateJdMatchResponse scoreResumeAgainstJd(String resumeText, String jdText) {
        int cap = Math.max(2000, matchMaxCharsPerDoc);
        String resume = CommonUtils.truncate(resumeText == null ? "" : resumeText, cap);
        String jd = CommonUtils.truncate(jdText == null ? "" : jdText, cap);
        String prompt = buildScoringPrompt(resume, jd);
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

    private String buildScoringPrompt(String resume, String jd) {
        return """
                Compare RESUME to JD. Output one JSON object only, no markdown.
                Keys: overall_score (0-100 int), summary (2 sentences max), strengths (max 5 strings), gaps (max 5 strings), recommendation (5 words max).

                JD:
                %s

                RESUME:
                %s
                """.formatted(jd, resume);
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
