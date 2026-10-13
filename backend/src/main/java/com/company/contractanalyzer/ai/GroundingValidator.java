package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.ingestion.TextNormalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Hallucination control. The model's evidence is accepted only if it can be verified
 * mechanically against the excerpts the server actually sent.
 *
 * <ol>
 *   <li>Every cited excerpt id must exist in this prompt.</li>
 *   <li>The evidence quote, after whitespace, case and quote-style normalisation, must be a
 *       substring of one of the cited excerpts and at least {@value #MIN_QUOTE_LENGTH}
 *       characters long. A very short quote such as "the Supplier" would verify trivially.</li>
 *   <li>COMPLIANT, PARTIAL and NON_COMPLIANT need both. Otherwise the result is downgraded to
 *       NEEDS_REVIEW, its confidence is capped, and the explanation says why.</li>
 *   <li>For NOT_FOUND or NEEDS_REVIEW, a quote that verifies is kept and one that does not is
 *       dropped.</li>
 * </ol>
 * The evidence page is the page of the excerpt that contains the quote, never a number
 * taken from model text.
 */
@Component
public class GroundingValidator {

    public static final int MIN_QUOTE_LENGTH = 12;

    /** A judgement after grounding. {@code grounded} is true when the evidence verified. */
    public record Grounded(FindingStatus status, double confidence, String explanation, String evidenceQuote,
                           Integer evidencePage, String recommendation, boolean grounded, String failureReason) {
    }

    public Grounded validate(RuleJudgement judgement, List<Excerpt> excerpts, double confidenceCap) {
        Map<String, Excerpt> byId = new HashMap<>();
        for (Excerpt excerpt : excerpts) {
            byId.put(excerpt.id(), excerpt);
        }
        List<String> unknownIds = new ArrayList<>();
        List<Excerpt> cited = new ArrayList<>();
        for (String id : judgement.citedIds()) {
            String normalizedId = id == null ? "" : id.strip().toUpperCase();
            Excerpt excerpt = byId.get(normalizedId);
            if (excerpt == null) {
                unknownIds.add(id);
            } else if (!cited.contains(excerpt)) {
                cited.add(excerpt);
            }
        }
        Excerpt containing = findQuote(judgement.evidenceQuote(), cited);
        double confidence = judgement.confidence();

        if (!judgement.status().requiresEvidence()) {
            return new Grounded(judgement.status(), confidence, judgement.explanation(),
                    containing == null ? null : judgement.evidenceQuote().strip(),
                    containing == null ? null : containing.chunk().page(),
                    judgement.recommendation(), containing != null, null);
        }

        String failure = null;
        if (cited.isEmpty()) {
            failure = unknownIds.isEmpty() ? "no excerpt was cited" : "cited excerpt ids " + unknownIds + " do not exist";
        } else if (!unknownIds.isEmpty()) {
            failure = "cited excerpt ids " + unknownIds + " do not exist";
        } else if (judgement.evidenceQuote() == null || judgement.evidenceQuote().isBlank()) {
            failure = "no evidence quote was given";
        } else if (TextNormalizer.canonical(judgement.evidenceQuote()).length() < MIN_QUOTE_LENGTH) {
            failure = "the evidence quote is too short to verify";
        } else if (containing == null) {
            failure = "the evidence quote does not appear verbatim in the cited excerpts";
        }

        if (failure == null) {
            return new Grounded(judgement.status(), confidence, judgement.explanation(),
                    judgement.evidenceQuote().strip(), containing.chunk().page(), judgement.recommendation(), true, null);
        }
        String explanation = judgement.explanation() + " [Grounding check failed: " + failure
                + ". The model's original assessment was " + judgement.status() + "; verify manually.]";
        return new Grounded(FindingStatus.NEEDS_REVIEW, Math.min(confidence, confidenceCap), explanation,
                null, null, judgement.recommendation(), false, failure);
    }

    private static Excerpt findQuote(String quote, List<Excerpt> candidates) {
        if (quote == null || quote.isBlank()) {
            return null;
        }
        String needle = TextNormalizer.canonical(stripWrappingQuotes(quote));
        if (needle.length() < MIN_QUOTE_LENGTH) {
            return null;
        }
        for (Excerpt excerpt : candidates) {
            if (TextNormalizer.canonical(excerpt.chunk().text()).contains(needle)) {
                return excerpt;
            }
        }
        return null;
    }

    /** Models often wrap the quote in quotation marks or end it with an ellipsis. */
    static String stripWrappingQuotes(String quote) {
        String q = quote.strip();
        if (q.endsWith("...") || q.endsWith("…")) {
            q = q.substring(0, q.length() - (q.endsWith("...") ? 3 : 1)).strip();
        }
        if (q.length() >= 2) {
            char first = q.charAt(0);
            char last = q.charAt(q.length() - 1);
            if ((first == '"' || first == '“' || first == '\'') && (last == '"' || last == '”' || last == '\'')) {
                q = q.substring(1, q.length() - 1).strip();
            }
        }
        return q;
    }
}
