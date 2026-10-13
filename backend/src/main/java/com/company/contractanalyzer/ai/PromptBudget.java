package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.ingestion.TokenEstimator;
import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Keeps prompts inside the model's context window ({@code OLLAMA_NUM_CTX}, default 4096).
 *
 * <p>Available tokens are {@code numCtx - reservedOutputTokens - fixedPromptTokens}. Excerpts are
 * sorted by similarity score, descending, and kept while they fit. The first excerpt that does
 * not fit ends the selection, so the lowest-scoring excerpts are always dropped first and a
 * lower-scored excerpt never displaces a higher-scored one. Ollama truncates prompts that
 * exceed num_ctx silently from the start, which would remove the instructions. That is why
 * this budget is enforced before the call.
 */
@Component
public class PromptBudget {

    /** Per-excerpt overhead for the delimiter lines ({@code <<<EXCERPT E1 | page 3 | ...>>>}). */
    static final int EXCERPT_OVERHEAD_TOKENS = 24;

    private final int numCtx;
    private final int reservedOutputTokens;

    @Autowired
    public PromptBudget(AppProperties properties) {
        this(properties.getLlm().getNumCtx(), properties.getLlm().getReservedOutputTokens());
    }

    public PromptBudget(int numCtx, int reservedOutputTokens) {
        this.numCtx = numCtx;
        this.reservedOutputTokens = reservedOutputTokens;
    }

    public int availableFor(int fixedPromptTokens) {
        return Math.max(0, numCtx - reservedOutputTokens - fixedPromptTokens);
    }

    /** @return the highest-scoring prefix of {@code chunks} that fits, ordered by score descending */
    public List<RetrievedChunk> fit(List<RetrievedChunk> chunks, int fixedPromptTokens) {
        List<RetrievedChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                .thenComparingInt(RetrievedChunk::chunkIndex));
        int available = availableFor(fixedPromptTokens);
        List<RetrievedChunk> kept = new ArrayList<>();
        int used = 0;
        for (RetrievedChunk chunk : sorted) {
            int cost = TokenEstimator.estimate(chunk.text()) + EXCERPT_OVERHEAD_TOKENS;
            if (used + cost > available) {
                break;
            }
            kept.add(chunk);
            used += cost;
        }
        return kept;
    }

    public int numCtx() {
        return numCtx;
    }
}
