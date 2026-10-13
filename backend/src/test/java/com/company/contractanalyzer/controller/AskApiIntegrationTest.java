package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.ai.ContractQaAgent;
import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Q&A through the real RetrievalAugmentationAdvisor and PgVectorStore, with a scripted model. */
class AskApiIntegrationTest extends AbstractIntegrationTest {

    private UUID contract;

    @BeforeEach
    void ingest() throws Exception {
        resetState();
        contract = uploadAndAwait(SamplePdfGenerator.wellFormedContract(), "contract-wellformed.pdf", "READY");
    }

    @Test
    void groundedAnswerCarriesServerSideCitations() throws Exception {
        stubChatModel.thenReply("The Customer may terminate for convenience on sixty days written notice (page 3).");

        JsonNode answer = json(ask("How much notice does the customer need to terminate for convenience?")
                .andExpect(status().isOk()));

        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("mode").asText()).isEqualTo("rag");
        assertThat(answer.get("answer").asText()).contains("sixty days");
        assertThat(answer.get("citations").size()).isPositive();
        assertThat(answer.get("citations").get(0).get("snippet").asText()).isNotBlank();
        assertThat(answer.get("citations").get(0).get("page").asInt()).isPositive();
    }

    @Test
    void unrelatedQuestionIsNotGrounded() throws Exception {
        stubChatModel.thenReply("It is probably blue.");

        JsonNode answer = json(ask("xylophone zebra quantum").andExpect(status().isOk()));

        assertThat(answer.get("grounded").asBoolean()).isFalse();
        assertThat(answer.get("citations").size()).isZero();
        assertThat(answer.get("answer").asText()).isEqualTo(ContractQaAgent.NOT_FOUND_ANSWER);
    }

    @Test
    void llmUnavailableIs503AndNotReadyIs409() throws Exception {
        llmAvailability.setReachable(false);
        ask("What is the governing law?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));

        llmAvailability.setReachable(true);
        jdbc.update("UPDATE contracts SET status = 'FAILED' WHERE id = ?", contract);
        ask("What is the governing law?").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_NOT_READY"));
    }

    @Test
    void questionIsValidated() throws Exception {
        ask("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.question").exists());
    }

    private ResultActions ask(String question) throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/" + contract + "/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("question", question))));
    }
}
