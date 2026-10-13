package com.company.contractanalyzer.repository;

import java.util.UUID;

/**
 * A chunk returned by similarity search.
 *
 * @param score cosine similarity in [0, 1] as reported by PgVectorStore (1 - cosine distance)
 */
public record RetrievedChunk(String id, UUID contractId, int page, int chunkIndex, String heading,
                             String text, double score) {
}
