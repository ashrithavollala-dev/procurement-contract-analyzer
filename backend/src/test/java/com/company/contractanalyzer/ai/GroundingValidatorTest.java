package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroundingValidatorTest {

    private static final UUID CONTRACT = UUID.randomUUID();
    private final GroundingValidator validator = new GroundingValidator();
    private final List<Excerpt> excerpts = Excerpt.number(List.of(
            chunk("a", 2, "8.1 The aggregate liability of each party shall not exceed the total fees paid in the twelve (12) months."),
            chunk("b", 5, "12.1 This Agreement is governed by the laws of the fictional State of Westmarch.")));

    @Test
    void validVerbatimQuoteIsGroundedOnTheCitedExcerptsPage() {
        GroundingValidator.Grounded result = validator.validate(judgement(FindingStatus.COMPLIANT, 0.9,
                List.of("E1"), "aggregate liability of each party shall not exceed the total fees paid"), excerpts, 0.3);

        assertThat(result.grounded()).isTrue();
        assertThat(result.status()).isEqualTo(FindingStatus.COMPLIANT);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.evidencePage()).isEqualTo(2);
    }

    @Test
    void normalisesWhitespaceCaseQuotesAndWrappingQuotationMarks() {
        GroundingValidator.Grounded result = validator.validate(judgement(FindingStatus.PARTIAL, 0.7, List.of(" e2 "),
                "“GOVERNED  BY the laws\n of the fictional state of westmarch”"), excerpts, 0.3);

        assertThat(result.grounded()).isTrue();
        assertThat(result.evidencePage()).isEqualTo(5);
    }

    @Test
    void quoteNotInCitedExcerptIsDowngradedWithCappedConfidence() {
        GroundingValidator.Grounded result = validator.validate(judgement(FindingStatus.COMPLIANT, 0.95, List.of("E1"),
                "liability is unlimited for all claims whatsoever"), excerpts, 0.3);

        assertThat(result.grounded()).isFalse();
        assertThat(result.status()).isEqualTo(FindingStatus.NEEDS_REVIEW);
        assertThat(result.confidence()).isEqualTo(0.3);
        assertThat(result.evidenceQuote()).isNull();
        assertThat(result.evidencePage()).isNull();
        assertThat(result.explanation()).contains("Grounding check failed").contains("COMPLIANT");
    }

    @Test
    void quoteFromAnUncitedExcerptDoesNotCount() {
        GroundingValidator.Grounded result = validator.validate(judgement(FindingStatus.NON_COMPLIANT, 0.8, List.of("E1"),
                "governed by the laws of the fictional State of Westmarch"), excerpts, 0.3);

        assertThat(result.status()).isEqualTo(FindingStatus.NEEDS_REVIEW);
    }

    @Test
    void missingQuoteIsDowngraded() {
        GroundingValidator.Grounded result = validator.validate(
                judgement(FindingStatus.COMPLIANT, 0.8, List.of("E1"), "  "), excerpts, 0.3);

        assertThat(result.status()).isEqualTo(FindingStatus.NEEDS_REVIEW);
        assertThat(result.failureReason()).contains("no evidence quote");
    }

    @Test
    void unknownExcerptIdIsDowngraded() {
        GroundingValidator.Grounded result = validator.validate(judgement(FindingStatus.COMPLIANT, 0.8, List.of("E7"),
                "aggregate liability of each party shall not exceed"), excerpts, 0.3);

        assertThat(result.status()).isEqualTo(FindingStatus.NEEDS_REVIEW);
        assertThat(result.failureReason()).contains("E7");
    }

    @Test
    void trivialShortQuoteIsRejected() {
        GroundingValidator.Grounded result = validator.validate(
                judgement(FindingStatus.COMPLIANT, 0.8, List.of("E1"), "the party"), excerpts, 0.3);

        assertThat(result.status()).isEqualTo(FindingStatus.NEEDS_REVIEW);
    }

    @Test
    void notFoundNeedsNoEvidenceAndDropsUnverifiableQuote() {
        GroundingValidator.Grounded result = validator.validate(
                judgement(FindingStatus.NOT_FOUND, 0.7, List.of(), "invented sentence that is nowhere"), excerpts, 0.3);

        assertThat(result.status()).isEqualTo(FindingStatus.NOT_FOUND);
        assertThat(result.confidence()).isEqualTo(0.7);
        assertThat(result.evidenceQuote()).isNull();
    }

    @Test
    void lowConfidenceIsNotRaisedByTheCap() {
        GroundingValidator.Grounded result = validator.validate(
                judgement(FindingStatus.COMPLIANT, 0.1, List.of(), null), excerpts, 0.3);

        assertThat(result.confidence()).isEqualTo(0.1);
    }

    private static RuleJudgement judgement(FindingStatus status, double confidence, List<String> ids, String quote) {
        return new RuleJudgement(status, confidence, "Model explanation.", ids, quote, "Recommendation.");
    }

    static RetrievedChunk chunk(String id, int page, String text) {
        return new RetrievedChunk(id, CONTRACT, page, page * 10, null, text, 0.8);
    }
}
