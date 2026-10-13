package com.company.contractanalyzer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the Flyway-owned schema: extension, tables, vector column, indexes, constraints, seed data. */
class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void vectorExtensionIsInstalled() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void bothMigrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void vectorStoreMatchesPgVectorStoreLayoutWith384Dimensions() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'vector_store' ORDER BY ordinal_position",
                String.class);
        assertThat(columns).containsExactly("id", "content", "metadata", "embedding");
        String embeddingType = jdbc.queryForObject(
                "SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a "
                        + "WHERE a.attrelid = 'vector_store'::regclass AND a.attname = 'embedding'", String.class);
        assertThat(embeddingType).isEqualTo("vector(384)");
        String metadataType = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'vector_store' AND column_name = 'metadata'",
                String.class);
        assertThat(metadataType).isEqualTo("jsonb");
    }

    @Test
    void hnswCosineIndexAndContractIdIndexesExist() {
        List<String> indexDefs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'vector_store'", String.class);
        assertThat(indexDefs).anySatisfy(def -> assertThat(def).contains("USING hnsw").contains("vector_cosine_ops"));
        assertThat(indexDefs).anySatisfy(def -> assertThat(def).contains("'contractId'"));
        assertThat(indexDefs).anySatisfy(def -> assertThat(def).contains("USING gin").contains("jsonb_path_ops"));
    }

    @Test
    void twelveRulesAreSeededWithExpectedCodes() {
        List<String> codes = jdbc.queryForList("SELECT code FROM compliance_rules ORDER BY code", String.class);
        assertThat(codes).contains("PAYMENT_TERMS", "TERMINATION_CONVENIENCE", "LIABILITY_CAP", "INDEMNIFICATION",
                "CONFIDENTIALITY", "GOVERNING_LAW", "IP_OWNERSHIP", "DATA_PROTECTION", "SLA_PENALTIES",
                "FORCE_MAJEURE", "AUTO_RENEWAL", "AUDIT_RIGHTS");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM compliance_rules WHERE code IN ('PAYMENT_TERMS','TERMINATION_CONVENIENCE',"
                        + "'LIABILITY_CAP','INDEMNIFICATION','CONFIDENTIALITY','GOVERNING_LAW','IP_OWNERSHIP',"
                        + "'DATA_PROTECTION','SLA_PENALTIES','FORCE_MAJEURE','AUTO_RENEWAL','AUDIT_RIGHTS')",
                Integer.class)).isEqualTo(12);
    }

    @Test
    void checkConstraintsRejectInvalidStatusAndConfidence() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO contracts (id, title, original_filename, content_hash, file_size_bytes, status, created_at, updated_at) "
                        + "VALUES (?, 't', 'f.pdf', ?, 1, 'BOGUS', now(), now())",
                UUID.randomUUID(), "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID contractId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO contracts (id, title, original_filename, content_hash, file_size_bytes, status, created_at, updated_at) "
                + "VALUES (?, 't', 'f.pdf', ?, 1, 'READY', now(), now())", contractId, uniqueHash());
        jdbc.update("INSERT INTO audit_runs (id, contract_id, status, total_rules, completed_rules, created_at, updated_at) "
                + "VALUES (?, ?, 'RUNNING', 1, 0, now(), now())", runId, contractId);
        Long ruleId = jdbc.queryForObject("SELECT id FROM compliance_rules WHERE code = 'LIABILITY_CAP'", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO audit_findings (audit_run_id, rule_id, status, confidence, iterations, created_at, updated_at) "
                        + "VALUES (?, ?, 'COMPLIANT', 1.5, 1, now(), now())", runId, ruleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE audit_runs SET completed_rules = 5 WHERE id = ?", runId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Deleting the contract cascades to its audit runs.
        jdbc.update("DELETE FROM contracts WHERE id = ?", contractId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_runs WHERE id = ?", Integer.class, runId)).isZero();
    }

    private static String uniqueHash() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return (hex + hex).substring(0, 64);
    }
}
