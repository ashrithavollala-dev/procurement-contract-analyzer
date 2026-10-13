package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.enums.FindingStatus;
import java.util.List;

/**
 * Final, grounded outcome of the plan-act-observe loop for one rule, ready to persist.
 *
 * @param confidence   0..1, already capped by grounding when evidence did not verify
 * @param evidencePage page of the excerpt that contains the verified quote (null if none)
 * @param iterations   number of retrieve-judge passes performed (1..max-iterations)
 * @param queriesUsed  retrieval queries in the order they were issued
 * @param errorCode    set when the rule degraded to NEEDS_REVIEW because of an LLM problem
 */
public record RuleEvaluation(
        FindingStatus status,
        double confidence,
        String explanation,
        String evidenceQuote,
        Integer evidencePage,
        String recommendation,
        int iterations,
        List<String> queriesUsed,
        ErrorCode errorCode) {

    public RuleEvaluation {
        queriesUsed = queriesUsed == null ? List.of() : List.copyOf(queriesUsed);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    /** A rule that could not be judged (timeout, unreachable model, invalid output, unexpected error). */
    public static RuleEvaluation needsReview(ErrorCode errorCode, String explanation, int iterations,
                                             List<String> queriesUsed) {
        return new RuleEvaluation(FindingStatus.NEEDS_REVIEW, 0.0, explanation, null, null,
                "Review this clause manually.", iterations, queriesUsed, errorCode);
    }
}
