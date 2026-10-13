package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.FileStorageService;
import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end ingestion through the HTTP API with Testcontainers pgvector and the sample PDFs. */
class ContractApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private VectorChunkRepository vectors;
    @Autowired private FileStorageService storage;

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void uploadIsAcceptedThenIngestedAsynchronouslyToReady() throws Exception {
        byte[] pdf = SamplePdfGenerator.wellFormedContract();
        JsonNode accepted = json(upload(pdf, "contract-wellformed.pdf", "application/pdf")
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.storedPath").doesNotExist())
                .andExpect(jsonPath("$.contentHash").isString()));
        UUID id = UUID.fromString(accepted.get("id").asText());

        awaitContractStatus(id, "READY");

        JsonNode ready = getJson("/api/v1/contracts/" + id);
        assertThat(ready.get("pageCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(ready.get("chunkCount").asInt()).isGreaterThanOrEqualTo(12);
        assertThat(ready.get("processedAt").isNull()).isFalse();
        assertThat(vectors.countByContractId(id)).isEqualTo(ready.get("chunkCount").asLong());

        JsonNode page = getJson("/api/v1/contracts?status=READY&q=wellformed&page=0&size=10");
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(id.toString());
        assertThat(page.has("pageable")).isFalse();

        byte[] downloaded = mockMvc.perform(get("/api/v1/contracts/" + id + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(Arrays.equals(downloaded, pdf)).as("download returns the original bytes").isTrue();
    }

    @Test
    void duplicateUploadIsRejectedWithTheExistingContractId() throws Exception {
        byte[] pdf = SamplePdfGenerator.weakContract();
        UUID first = UUID.fromString(json(upload(pdf, "contract-weak.pdf", "application/pdf")).get("id").asText());

        upload(pdf, "copy-of-weak.pdf", "application/pdf")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CONTRACT"))
                .andExpect(jsonPath("$.details.existingContractId").value(first.toString()));
        awaitContractStatus(first, "READY");
    }

    @Test
    void nonPdfUploadsAreRejected() throws Exception {
        upload("just text".getBytes(StandardCharsets.UTF_8), "notes.txt", "text/plain")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload("PK fake zip".getBytes(StandardCharsets.UTF_8), "contract.pdf", "application/octet-stream")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload("%PDF-1.7 but corrupt".getBytes(StandardCharsets.US_ASCII), "broken.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType());
        upload(new byte[0], "empty.pdf", "application/pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.file").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contracts", Integer.class)).isZero();
    }

    @Test
    void oversizedUploadIsFileTooLarge() throws Exception {
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);

        upload(big, "big.pdf", "application/pdf")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void imageOnlyPdfFailsWithNoExtractableText() throws Exception {
        UUID id = uploadAndAwait(SamplePdfGenerator.imageOnlyContract(), "contract-scanned-image-only.pdf", "FAILED");

        JsonNode failed = getJson("/api/v1/contracts/" + id);
        assertThat(failed.get("errorCode").asText()).isEqualTo("NO_EXTRACTABLE_TEXT");
        assertThat(failed.get("errorMessage").asText()).doesNotContain("Exception").doesNotContain("\tat ");
        assertThat(vectors.countByContractId(id)).isZero();
    }

    @Test
    void reprocessIsIdempotentAndGuardedByStatus() throws Exception {
        UUID id = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
        long vectorsBefore = vectors.countByContractId(id);

        mockMvc.perform(post("/api/v1/contracts/" + id + "/reprocess"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_READY"));

        mockMvc.perform(post("/api/v1/contracts/" + id + "/reprocess?force=true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        awaitContractStatus(id, "READY");

        assertThat(vectors.countByContractId(id)).isEqualTo(vectorsBefore);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT id) FROM vector_store", Long.class)).isEqualTo(vectorsBefore);
    }

    @Test
    void failedContractCanBeReprocessed() throws Exception {
        UUID id = uploadAndAwait(SamplePdfGenerator.imageOnlyContract(), "contract-scanned-image-only.pdf", "FAILED");

        mockMvc.perform(post("/api/v1/contracts/" + id + "/reprocess")).andExpect(status().isAccepted());

        awaitContractStatus(id, "FAILED");
        assertThat(getJson("/api/v1/contracts/" + id).get("errorCode").asText()).isEqualTo("NO_EXTRACTABLE_TEXT");
    }

    @Test
    void deleteRemovesVectorsFileAndRows() throws Exception {
        UUID id = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
        String storedPath = jdbc.queryForObject("SELECT stored_path FROM contracts WHERE id = ?", String.class, id);
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_runs (id, contract_id, status, total_rules, completed_rules, created_at, updated_at) "
                + "VALUES (?, ?, 'COMPLETED', 0, 0, now(), now())", runId, id);
        assertThat(Files.exists(storage.resolve(storedPath))).isTrue();

        mockMvc.perform(delete("/api/v1/contracts/" + id)).andExpect(status().isNoContent());

        assertThat(vectors.countByContractId(id)).isZero();
        assertThat(Files.exists(storage.resolve(storedPath))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_runs WHERE id = ?", Integer.class, runId)).isZero();
        mockMvc.perform(get("/api/v1/contracts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteIsRefusedWhileProcessing() throws Exception {
        UUID id = uploadAndAwait(SamplePdfGenerator.weakContract(), "contract-weak.pdf", "READY");
        jdbc.update("UPDATE contracts SET status = 'PROCESSING' WHERE id = ?", id);

        mockMvc.perform(delete("/api/v1/contracts/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_READY"))
                .andExpect(jsonPath("$.details.status").value("PROCESSING"));
    }
}
