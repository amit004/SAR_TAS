package com.sar.anaylze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JdStructuredResponse(
        @JsonProperty("_id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("skills_required_per_experience_level") List<String> skillsRequiredPerExperienceLevel,
        @JsonProperty("searchable_text") String searchableText,
        @JsonProperty("role_and_responsibilities") String roleAndResponsibilities,
        @JsonProperty("experience_reqd") String experienceReqd,
        @JsonProperty("total_industry_years_of_experience") String totalIndustryYearsOfExperience,
        @JsonProperty("max_compensation") String maxCompensation,
        @JsonProperty("location") String location,
        @JsonProperty("mode") String mode,
        @JsonProperty("createAt") String createAt,
        @JsonProperty("working_hours") String workingHours,
        @JsonProperty("notice_period") String noticePeriod,
        @JsonProperty("employment_type") String employmentType,
        @JsonProperty("number_of_positions") String numberOfPositions,
        @JsonProperty("notes") String notes
) {
}
