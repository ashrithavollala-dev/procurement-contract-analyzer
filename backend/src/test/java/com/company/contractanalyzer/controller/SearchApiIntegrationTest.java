package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Semantic search over real PgVectorStore rows (fake but deterministic embeddings); no LLM involved. */
class SearchApiIntegrationTest extends AbstractIntegrationTest {

    private UUID wellFormed;
    private UUID weak;

    @BeforeEach
    void ingestSamples() throws Exception {
        resetState();
        wellFormed = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
        weak = uploadAndAwait(SamplePdfGenerator.weakContract(), "contract-weak.pdf", "READY");
        // Proves search does not need the LLM.
        llmAvailability.setReachable(false);
    }

    @Test
    void returnsTheExpectedClauseWithScorePageAndTitle() throws Exception {
        JsonNode response = search("{\"query\":\"aggregate liability shall not exceed total fees\",\"topK\":3}");

        JsonNode top = response.get("results").get(0);
        assertThat(top.get("contractId").asText()).isEqualTo(wellFormed.toString());
        assertThat(top.get("contractTitle").asText()).isEqualTo("contract-wellformed");
        assertThat(top.get("snippet").asText()).contains("aggregate liability");
        assertThat(top.get("heading").asText()).startsWith("ARTICLE 8 LIMITATION OF LIABILITY");
        assertThat(top.get("page").asInt()).isPositive();
        assertThat(top.get("score").asDouble()).isBetween(0.0, 1.0);
        assertThat(response.get("results").size()).isLessThanOrEqualTo(3);
        double previous = 1.0;
        for (JsonNode result : response.get("results")) {
            assertThat(result.get("score").asDouble()).isLessThanOrEqualTo(previous);
            previous = result.get("score").asDouble();
        }
    }

    @Test
    void contractIdFilterRestrictsResultsToThatContract() throws Exception {
        JsonNode response = search("{\"query\":\"governed by the laws of Westmarch\",\"topK\":10,\"contractId\":\"" + weak + "\"}");

        assertThat(response.get("results").size()).isPositive();
        for (JsonNode result : response.get("results")) {
            assertThat(result.get("contractId").asText()).isEqualTo(weak.toString());
        }
    }

    @Test
    void minScoreFiltersWeakMatches() throws Exception {
        JsonNode response = search("{\"query\":\"xylophone zebra quantum\",\"topK\":10,\"minScore\":0.5}");

        assertThat(response.get("results").size()).isZero();
    }

    @Test
    void unknownContractFilterIs404() throws Exception {
        mockMvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"liability\",\"contractId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private JsonNode search(String body) throws Exception {
        return json(mockMvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));
    }
}
