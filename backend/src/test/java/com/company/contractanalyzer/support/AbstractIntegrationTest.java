package com.company.contractanalyzer.support;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base class for Testcontainers-backed integration tests. Requires a running Docker engine.
 * All subclasses share one cached application context and one pgvector container, so each
 * test starts from {@link #resetState()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestModelConfig.class})
public abstract class AbstractIntegrationTest {

    protected static final Duration INGESTION_TIMEOUT = Duration.ofSeconds(60);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StubChatModel stubChatModel;
    @Autowired protected ControllableLlmAvailability llmAvailability;

    /** Clean data (rules keep their seed rows, reactivated), a fresh stub script and a reachable LLM. */
    protected void resetState() {
        jdbc.execute("TRUNCATE audit_findings, audit_runs, contracts CASCADE");
        jdbc.execute("DELETE FROM vector_store");
        jdbc.update("DELETE FROM compliance_rules WHERE id > 12");
        jdbc.update("UPDATE compliance_rules SET active = TRUE");
        stubChatModel.reset();
        llmAvailability.setReachable(true);
    }

    protected ResultActions upload(byte[] content, String filename, String contentType) throws Exception {
        return mockMvc.perform(multipart("/api/v1/contracts")
                .file(new MockMultipartFile("file", filename, contentType, content))
                .param("title", filename.replace(".pdf", ""))
                .param("vendorName", "Fictional Vendor Ltd"));
    }

    protected UUID uploadAndAwait(byte[] pdf, String filename, String expectedStatus) throws Exception {
        String body = upload(pdf, filename, "application/pdf").andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        awaitContractStatus(id, expectedStatus);
        return id;
    }

    protected void awaitContractStatus(UUID id, String status) {
        await().atMost(INGESTION_TIMEOUT).pollInterval(Duration.ofMillis(200))
                .until(() -> status.equals(getJson("/api/v1/contracts/" + id).path("status").asText()));
    }

    protected JsonNode getJson(String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString());
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
