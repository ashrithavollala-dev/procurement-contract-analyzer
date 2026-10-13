package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ComplianceRuleApiIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void listsSeededRulesOrderedByCode() throws Exception {
        JsonNode rules = getJson("/api/v1/compliance-rules");

        assertThat(rules).hasSize(12);
        assertThat(rules.get(0).get("code").asText()).isEqualTo("AUDIT_RIGHTS");
        assertThat(rules.get(0).has("version")).isFalse();
    }

    @Test
    void createUpdateAndToggleARule() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v1/compliance-rules").contentType(MediaType.APPLICATION_JSON)
                        .content(ErrorPayloadIntegrationTest.rule("WARRANTY_PERIOD")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.active").value(true)));
        long id = created.get("id").asLong();

        mockMvc.perform(put("/api/v1/compliance-rules/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Warranty period (goods)\",\"question\":\"Is a warranty of at least 12 months given?\","
                                + "\"guidance\":\"Compliant: 12 months or more.\",\"severity\":\"HIGH\",\"mandatory\":true,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WARRANTY_PERIOD"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.mandatory").value(true));

        mockMvc.perform(patch("/api/v1/compliance-rules/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(getJson("/api/v1/compliance-rules?activeOnly=true")).hasSize(12);
        assertThat(getJson("/api/v1/compliance-rules")).hasSize(13);

        mockMvc.perform(patch("/api/v1/compliance-rules/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.active").exists());
    }
}
