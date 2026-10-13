package com.company.contractanalyzer.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only native access to the Spring AI {@code vector_store} table for counts and
 * verification. Writes and deletes go through {@code VectorStore} so the vector store
 * abstraction stays the single writer. Uses the btree index on {@code metadata->>'contractId'}.
 */
@Repository
@RequiredArgsConstructor
public class VectorChunkRepository {

    private final JdbcTemplate jdbcTemplate;

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
}
