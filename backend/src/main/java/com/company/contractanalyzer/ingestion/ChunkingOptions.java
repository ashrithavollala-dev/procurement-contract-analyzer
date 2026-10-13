package com.company.contractanalyzer.ingestion;

/**
 * Chunk sizing in estimated tokens (see {@link TokenEstimator}).
 *
 * @param maxTokens     blocks above this size are split on sentence boundaries
 * @param overlapTokens trailing context repeated at the start of the next split chunk
 * @param minTokens     blocks below this size merge with the following block of the same article
 */
public record ChunkingOptions(int maxTokens, int overlapTokens, int minTokens) {

    public static final ChunkingOptions DEFAULTS = new ChunkingOptions(350, 50, 60);

    public ChunkingOptions {
        if (maxTokens < 20) {
            throw new IllegalArgumentException("maxTokens must be >= 20");
        }
        if (overlapTokens < 0 || overlapTokens >= maxTokens / 2) {
            throw new IllegalArgumentException("overlapTokens must be >= 0 and < maxTokens / 2");
        }
        if (minTokens < 0 || minTokens >= maxTokens) {
            throw new IllegalArgumentException("minTokens must be >= 0 and < maxTokens");
        }
    }
}
