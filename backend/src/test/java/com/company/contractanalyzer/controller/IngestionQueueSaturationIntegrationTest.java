package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.config.AsyncConfig;
import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

/**
 * Executor saturation. A 1-thread ingestion pool with no queue is occupied by a blocking task,
 * so the next upload is rejected (AbortPolicy). It is persisted as FAILED/INGESTION_QUEUE_FULL
 * and the client gets 503. This test uses its own context because the pool sizes differ.
 */
@TestPropertySource(properties = {
    "app.ingestion.core-pool-size=1",
    "app.ingestion.max-pool-size=1",
    "app.ingestion.queue-capacity=0"
})
class IngestionQueueSaturationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier(AsyncConfig.INGESTION_EXECUTOR)
    private ThreadPoolTaskExecutor ingestionExecutor;

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void fullQueueYields503AndAFailedContractThatCanBeReprocessed() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        ingestionExecutor.execute(() -> {
            occupied.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

        JsonNode error = json(upload(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "application/pdf")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INGESTION_QUEUE_FULL")));
        UUID id = UUID.fromString(error.get("details").get("contractId").asText());

        JsonNode contract = getJson("/api/v1/contracts/" + id);
        assertThat(contract.get("status").asText()).isEqualTo("FAILED");
        assertThat(contract.get("errorCode").asText()).isEqualTo("INGESTION_QUEUE_FULL");

        release.countDown();
        // The executor thread may need a moment to become free again.
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> ingestionExecutor.getActiveCount() == 0);
        mockMvc.perform(post("/api/v1/contracts/" + id + "/reprocess")).andExpect(status().isAccepted());
        awaitContractStatus(id, "READY");
    }
}
