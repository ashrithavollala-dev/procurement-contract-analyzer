package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.ExcerptQuotingResponder;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;

/** The full audit flow over HTTP: 202, async agent loop, grounded findings, deterministic score. */
class AuditApiIntegrationTest extends AbstractIntegrationTest {

    private static final String THREE_RULES = "{\"ruleCodes\":[\"LIABILITY_CAP\",\"AUDIT_RIGHTS\",\"DATA_PROTECTION\"]}";

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void wellFormedContractAuditIsGroundedCompliantAndLowRisk() throws Exception {
        UUID contract = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
        stubChatModel.otherwiseReply(new ExcerptQuotingResponder(Set.of()));

        JsonNode audit = awaitAudit(startAudit(contract, THREE_RULES), "COMPLETED");

        assertThat(audit.get("totalRules").asInt()).isEqualTo(3);
        assertThat(audit.get("completedRules").asInt()).isEqualTo(3);
        assertThat(audit.get("riskScore").decimalValue()).as(audit.toPrettyString()).isEqualByComparingTo("0.0");
        assertThat(audit.get("riskBand").asText()).isEqualTo("LOW");
        assertThat(audit.get("summary").asText()).startsWith("3 rules evaluated: 3 compliant");
        assertThat(audit.get("findings")).hasSize(3);
        for (JsonNode finding : audit.get("findings")) {
            assertThat(finding.get("status").asText()).isEqualTo("COMPLIANT");
            assertThat(finding.get("evidencePage").asInt()).isPositive();
            assertThat(finding.get("evidenceQuote").asText()).isNotBlank();
            assertThat(finding.get("queriesUsed")).isNotEmpty();
            assertThat(finding.get("iterations").asInt()).isEqualTo(1);
        }
        // CRITICAL findings are listed first.
        assertThat(audit.get("findings").get(0).get("severity").asText()).isEqualTo("CRITICAL");

        JsonNode history = getJson("/api/v1/contracts/" + contract + "/audits");
        assertThat(history).hasSize(1);
        JsonNode dashboard = getJson("/api/v1/dashboard/summary");
        assertThat(dashboard.get("contractsByStatus").get("READY").asInt()).isEqualTo(1);
        assertThat(dashboard.get("averageRiskScore").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(dashboard.get("recentAudits")).hasSize(1);
    }

    @Test
    void weakContractMissingMandatoryClausesScoresCritical() throws Exception {
        UUID contract = uploadAndAwait(SamplePdfGenerator.weakContract(), "contract-weak.pdf", "READY");
        stubChatModel.otherwiseReply(new ExcerptQuotingResponder(Set.of("LIABILITY_CAP", "AUDIT_RIGHTS", "DATA_PROTECTION")));

        JsonNode audit = awaitAudit(startAudit(contract, THREE_RULES), "COMPLETED");

        assertThat(audit.get("riskScore").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(audit.get("riskBand").asText()).isEqualTo("CRITICAL");
        for (JsonNode finding : audit.get("findings")) {
            assertThat(finding.get("status").asText()).isEqualTo("NOT_FOUND");
            // NOT_FOUND triggers the bounded reflection pass.
            assertThat(finding.get("iterations").asInt()).isEqualTo(2);
            assertThat(finding.get("queriesUsed")).hasSize(2);
        }
        JsonNode dashboard = getJson("/api/v1/dashboard/summary");
        assertThat(dashboard.get("topNonCompliantRules")).hasSize(3);
    }

    @Test
    void threeConsecutiveUnreachableRulesAbortTheRun() throws Exception {
        UUID contract = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
        for (int i = 0; i < 3; i++) {
            stubChatModel.thenThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));
        }

        JsonNode audit = awaitAudit(startAudit(contract,
                "{\"ruleCodes\":[\"LIABILITY_CAP\",\"AUDIT_RIGHTS\",\"DATA_PROTECTION\",\"GOVERNING_LAW\"]}"), "FAILED");

        assertThat(audit.get("errorCode").asText()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(audit.get("completedRules").asInt()).isEqualTo(3);
        assertThat(audit.get("riskScore").isNull()).isTrue();
        for (JsonNode finding : audit.get("findings")) {
            assertThat(finding.get("status").asText()).isEqualTo("NEEDS_REVIEW");
            assertThat(finding.get("errorCode").asText()).isEqualTo("LLM_UNAVAILABLE");
        }
    }

    @Test
    void preconditionsAreEnforced() throws Exception {
        UUID failed = uploadAndAwait(SamplePdfGenerator.imageOnlyContract(), "contract-scanned-image-only.pdf", "FAILED");
        UUID ready = uploadAndAwait(SamplePdfGenerator.weakContract(), "contract-weak.pdf", "READY");

        postAudit(failed, THREE_RULES).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_READY"));
        postAudit(ready, "{\"ruleCodes\":[\"NOT_A_RULE\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.ruleCodes").value("Unknown rule codes: NOT_A_RULE"));
        postAudit(ready, "{\"ruleCodes\":[\"lower_case\"]}").andExpect(status().isBadRequest());
        postAudit(UUID.randomUUID(), THREE_RULES).andExpect(status().isNotFound());

        jdbc.update("UPDATE compliance_rules SET active = FALSE");
        postAudit(ready, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_ACTIVE_RULES"));
        jdbc.update("UPDATE compliance_rules SET active = TRUE");

        llmAvailability.setReachable(false);
        postAudit(ready, THREE_RULES).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_runs", Integer.class)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions postAudit(UUID contract, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/" + contract + "/audits")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID startAudit(UUID contract, String body) throws Exception {
        JsonNode accepted = json(postAudit(contract, body)
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED")));
        return UUID.fromString(accepted.get("auditId").asText());
    }

    private JsonNode awaitAudit(UUID auditId, String status) throws Exception {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> status.equals(getJson("/api/v1/audits/" + auditId).path("status").asText()));
        return getJson("/api/v1/audits/" + auditId);
    }
}
