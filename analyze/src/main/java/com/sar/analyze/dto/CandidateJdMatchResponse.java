package com.sar.anaylze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CandidateJdMatchResponse(
        @JsonProperty("overall_score") int overallScore,
        @JsonProperty("summary") String summary,
        @JsonProperty("strengths") List<String> strengths,
        @JsonProperty("gaps") List<String> gaps,
        @JsonProperty("recommendation") String recommendation) {
}
