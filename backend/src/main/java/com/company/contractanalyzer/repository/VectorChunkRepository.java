package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.ingestion.Chunk;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Data access for contract chunks in the Spring AI {@code vector_store} table. Writes, deletes
 * and similarity search go through {@link VectorStore} (PgVectorStore). Counts use native SQL
 * with the btree index on {@code metadata->>'contractId'}.
 *
 * <p>Never called inside a transaction. Embedding happens inside {@code VectorStore.add} before
 * PgVectorStore borrows a connection for its batch INSERT, so no connection is held while the
 * embedding model runs.
 */
@Repository
public class VectorChunkRepository {

    public static final String META_CONTRACT_ID = "contractId";
    public static final String META_PAGE = "page";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_HEADING = "heading";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public VectorChunkRepository(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Deterministic id per (contract, chunk index). PgVectorStore upserts on id, so re-running
     * ingestion cannot create duplicate vectors even before the explicit delete.
     */
    public static String vectorId(UUID contractId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((contractId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Embeds and stores chunks in batches of {@code batchSize} (one embedding call and one INSERT batch each). */
    public void addChunks(UUID contractId, List<Chunk> chunks, int batchSize) {
        for (int from = 0; from < chunks.size(); from += batchSize) {
            List<Chunk> batch = chunks.subList(from, Math.min(chunks.size(), from + batchSize));
            List<Document> documents = new ArrayList<>(batch.size());
            for (Chunk chunk : batch) {
                documents.add(toDocument(contractId, chunk));
            }
            vectorStore.add(documents);
        }
    }

    /** Idempotent: deleting a contract with no vectors is a no-op. */
    public void deleteByContractId(UUID contractId) {
        vectorStore.delete(contractFilter(contractId));
    }

    /**
     * Similarity search, optionally restricted to one contract through the vector store's
     * metadata filter expression.
     */
    public List<RetrievedChunk> search(String query, UUID contractId, int topK, double minScore) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(minScore);
        if (contractId != null) {
            request.filterExpression(contractFilter(contractId));
        }
        List<Document> documents = vectorStore.similaritySearch(request.build());
        List<RetrievedChunk> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            result.add(toRetrievedChunk(document));
        }
        return result;
    }

    public long countByContractId(UUID contractId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata ->> 'contractId' = ?",
                Long.class, contractId.toString());
        return count == null ? 0 : count;
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
        return count == null ? 0 : count;
    }

    public static Filter.Expression contractFilter(UUID contractId) {
        return new FilterExpressionBuilder().eq(META_CONTRACT_ID, contractId.toString()).build();
    }

    static Document toDocument(UUID contractId, Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_CONTRACT_ID, contractId.toString());
        metadata.put(META_PAGE, chunk.page());
        metadata.put(META_CHUNK_INDEX, chunk.chunkIndex());
        // Spring AI Document metadata may not contain null values.
        if (chunk.heading() != null) {
            metadata.put(META_HEADING, chunk.heading());
        }
        return Document.builder()
                .id(vectorId(contractId, chunk.chunkIndex()))
                .text(chunk.text())
                .metadata(metadata)
                .build();
    }

    public static RetrievedChunk toRetrievedChunk(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Object heading = metadata.get(META_HEADING);
        Double score = document.getScore();
        return new RetrievedChunk(
                document.getId(),
                UUID.fromString(String.valueOf(metadata.get(META_CONTRACT_ID))),
                intValue(metadata.get(META_PAGE)),
                intValue(metadata.get(META_CHUNK_INDEX)),
                heading == null ? null : heading.toString(),
                document.getText(),
                score == null ? 0.0 : score);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
