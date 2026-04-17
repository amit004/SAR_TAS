package com.sar.anaylze.util;

import com.sar.anaylze.dto.CandidateJdMatchResponse;
import com.sar.anaylze.dto.JdStructuredResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared helpers for SAR analyzer flows (fallback payloads, text preview).
 */
public final class SarUtils {

    private SarUtils() {
    }

    /**
     * Fallback when JD structuring via LLM is unavailable or failed.
     */
    public static JdStructuredResponse fallbackJdStructured(String jdText, String interviewRequirement) {
        return new JdStructuredResponse(
                UUID.randomUUID().toString(),
                "Unknown",
                List.of(),
                CommonUtils.searchableTextPreview(jdText),
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
                "LLM not configured or unavailable. Returning fallback response.");
    }

    /**
     * Fallback when resume–JD scoring via LLM is unavailable, failed, or could not be parsed.
     */
    public static CandidateJdMatchResponse fallbackCandidateMatch(String summaryNote) {
        String summary = summaryNote == null ? "" : summaryNote;
        return new CandidateJdMatchResponse(
                0,
                summary,
                List.of(),
                List.of(),
                "Unable to compute score.");
    }
}
