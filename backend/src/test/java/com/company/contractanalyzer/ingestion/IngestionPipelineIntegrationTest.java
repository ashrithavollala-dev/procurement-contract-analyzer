package com.company.contractanalyzer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.ContractLifecycleService;
import com.company.contractanalyzer.service.FileStorageService;
import com.company.contractanalyzer.support.AbstractIntegrationTest;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs the real pipeline (PDFBox, chunker, PgVectorStore) against Testcontainers pgvector with fake embeddings. */
class IngestionPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IngestionPipeline pipeline;
    @Autowired private ContractLifecycleService lifecycle;
    @Autowired private FileStorageService storage;
    @Autowired private ContractRepository contracts;
    @Autowired private VectorChunkRepository vectors;
    @Autowired private StartupRecovery recovery;

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void textPdfBecomesReadyWithSearchableChunks() throws Exception {
        UUID id = queue(PdfTextExtractorTest.pdf(List.of(
                List.of("ARTICLE 1 LIMITATION OF LIABILITY",
                        "1.1 The aggregate liability of the Supplier shall not exceed the total fees",
                        "paid by the Customer in the twelve months preceding the claim.",
                        "1.2 The cap does not apply to fraud, wilful misconduct or breach of confidentiality."),
                List.of("ARTICLE 2 GOVERNING LAW",
                        "2.1 This Agreement is governed by the laws of the fictional State of Westmarch.",
                        "2.2 The courts of Westmarch City have exclusive jurisdiction over any dispute."))));

        pipeline.process(id);

        Contract contract = contracts.findById(id).orElseThrow();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.READY);
        assertThat(contract.getPageCount()).isEqualTo(2);
        assertThat(contract.getChunkCount()).isPositive();
        assertThat(contract.getProcessedAt()).isNotNull();
        assertThat(vectors.countByContractId(id)).isEqualTo(contract.getChunkCount().longValue());

        List<RetrievedChunk> hits = vectors.search("governing law courts jurisdiction", id, 3, 0.0);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).text()).contains("Westmarch");
        assertThat(hits.get(0).page()).isEqualTo(2);
        assertThat(hits).allSatisfy(hit -> assertThat(hit.contractId()).isEqualTo(id));

        // Re-running is idempotent: deterministic ids + delete-first never duplicate vectors.
        long before = vectors.countByContractId(id);
        lifecycle.requeueForReprocess(id);
        pipeline.process(id);
        assertThat(vectors.countByContractId(id)).isEqualTo(before);
    }

    @Test
    void imageOnlyPdfFailsWithNoExtractableTextAndLeavesNoVectors() throws Exception {
        UUID id = queue(PdfTextExtractorTest.pdf(List.of(List.of(), List.of())));

        pipeline.process(id);

        Contract contract = contracts.findById(id).orElseThrow();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.FAILED);
        assertThat(contract.getErrorCode()).isEqualTo(ErrorCode.NO_EXTRACTABLE_TEXT.name());
        assertThat(contract.getErrorMessage()).contains("no extractable text");
        assertThat(vectors.countByContractId(id)).isZero();
    }

    @Test
    void startupRecoveryMarksInFlightContractsInterrupted() throws Exception {
        UUID id = queue(PdfTextExtractorTest.pdf(List.of(List.of("Pending contract text."))));
        jdbc.update("UPDATE contracts SET status = 'PROCESSING', updated_at = now() - interval '1 hour' WHERE id = ?", id);
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_runs (id, contract_id, status, total_rules, completed_rules, created_at, updated_at) "
                + "VALUES (?, ?, 'RUNNING', 12, 5, now() - interval '1 hour', now() - interval '1 hour')", runId, id);

        StartupRecovery.RecoveryResult result = recovery.recover();

        assertThat(result.contracts()).isEqualTo(1);
        assertThat(result.audits()).isEqualTo(1);
        Contract contract = contracts.findById(id).orElseThrow();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.FAILED);
        assertThat(contract.getErrorCode()).isEqualTo(ErrorCode.INTERRUPTED.name());
        assertThat(jdbc.queryForObject("SELECT status || ':' || error_code FROM audit_runs WHERE id = ?", String.class, runId))
                .isEqualTo("FAILED:INTERRUPTED");
        assertThat(jdbc.queryForObject("SELECT summary FROM audit_runs WHERE id = ?", String.class, runId))
                .contains("5 of 12");
    }

    private UUID queue(byte[] pdf) throws Exception {
        UUID id = UUID.randomUUID();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        lifecycle.createUploaded(id, "Test contract " + id, "Fictional Vendor", "test.pdf", hash, pdf.length);
        lifecycle.markQueued(id, storage.store(id, pdf));
        return id;
    }
}
