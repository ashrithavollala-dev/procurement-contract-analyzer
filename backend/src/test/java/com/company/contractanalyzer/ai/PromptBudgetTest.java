package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.ingestion.TokenEstimator;
import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Context-window budgeting: excerpts are dropped lowest-score first until the prompt fits num_ctx. */
class PromptBudgetTest {

    private static final UUID CONTRACT = UUID.randomUUID();
    private static final String TEXT_100_TOKENS = "word ".repeat(75).strip();

    @Test
    void keepsEverythingWhenItFitsSortedByScore() {
        PromptBudget budget = new PromptBudget(4096, 700);

        List<RetrievedChunk> kept = budget.fit(List.of(chunk("low", 0.2), chunk("high", 0.9), chunk("mid", 0.5)), 500);

        assertThat(kept).extracting(RetrievedChunk::id).containsExactly("high", "mid", "low");
    }

    @Test
    void dropsLowestScoringExcerptsFirstToFitTheContextWindow() {
        int perExcerpt = TokenEstimator.estimate(TEXT_100_TOKENS) + PromptBudget.EXCERPT_OVERHEAD_TOKENS;
        // numCtx 1000 - output 300 - fixed 100 = 600 tokens available -> floor(600 / perExcerpt) excerpts.
        PromptBudget budget = new PromptBudget(1000, 300);
        List<RetrievedChunk> candidates = List.of(
                chunk("s3", 0.3), chunk("s9", 0.9), chunk("s5", 0.5), chunk("s7", 0.7), chunk("s1", 0.1),
                chunk("s8", 0.8), chunk("s6", 0.6));

        List<RetrievedChunk> kept = budget.fit(candidates, 100);

        int expected = 600 / perExcerpt;
        assertThat(kept).hasSize(expected);
        assertThat(kept).extracting(RetrievedChunk::id).containsExactly(
                List.of("s9", "s8", "s7", "s6", "s5", "s3", "s1").subList(0, expected).toArray(String[]::new));
        int used = kept.stream().mapToInt(c -> TokenEstimator.estimate(c.text()) + PromptBudget.EXCERPT_OVERHEAD_TOKENS).sum();
        assertThat(100 + used).isLessThanOrEqualTo(1000 - 300);
    }

    @Test
    void neverSkipsAHigherScoredExcerptToSqueezeInALowerScoredSmallerOne() {
        PromptBudget budget = new PromptBudget(1000, 300);
        RetrievedChunk big = new RetrievedChunk("big", CONTRACT, 1, 1, null, "word ".repeat(600), 0.8);
        RetrievedChunk small = new RetrievedChunk("small", CONTRACT, 1, 2, null, "tiny clause", 0.4);

        assertThat(budget.fit(List.of(small, big), 100)).isEmpty();
    }

    @Test
    void fixedPromptLargerThanWindowLeavesNoRoom() {
        PromptBudget budget = new PromptBudget(1000, 300);

        assertThat(budget.availableFor(5000)).isZero();
        assertThat(budget.fit(List.of(chunk("a", 0.9)), 5000)).isEmpty();
    }

    private static RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk(id, CONTRACT, 1, (int) (score * 100), null, TEXT_100_TOKENS, score);
    }
}
