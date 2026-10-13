package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.FindingStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Structured output of one JUDGE call. Spring AI's BeanOutputConverter derives the JSON
 * schema from this record (sent to Ollama as {@code format}) and parses the reply into it.
 * The application then validates it ({@link #validationError()}) and checks its grounding.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"status", "confidence", "explanation", "citedExcerptIds", "evidenceQuote", "recommendation"})
public record RuleJudgement(
        @JsonProperty(required = true)
        @JsonPropertyDescription("One of COMPLIANT, PARTIAL, NON_COMPLIANT, NOT_FOUND, NEEDS_REVIEW")
        FindingStatus status,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Confidence between 0 and 1")
        Double confidence,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Two or three sentences explaining the assessment")
        String explanation,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Ids of the excerpts relied on, e.g. [\"E1\"]; empty when NOT_FOUND")
        List<String> citedExcerptIds,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Exact sentence copied word for word from a cited excerpt; empty when NOT_FOUND")
        String evidenceQuote,

        @JsonProperty(required = true)
        @JsonPropertyDescription("One sentence on what the buyer should do")
        String recommendation) {

    /** @return null when valid, otherwise why the structured output is unusable */
    public String validationError() {
        if (status == null) {
            return "status is missing or not one of the allowed values";
        }
        if (confidence == null || confidence.isNaN() || confidence < 0.0 || confidence > 1.0) {
            return "confidence must be a number between 0 and 1";
        }
        if (explanation == null || explanation.isBlank()) {
            return "explanation is missing";
        }
        if (citedExcerptIds == null) {
            return "citedExcerptIds is missing";
        }
        return null;
    }

    public List<String> citedIds() {
        return citedExcerptIds == null ? List.of() : citedExcerptIds;
    }
}
