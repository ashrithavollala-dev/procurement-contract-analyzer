package com.company.contractanalyzer.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Every error path produces the same payload: timestamp, status, error, code, message, path, details?. */
class ErrorPayloadIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void reset() {
        resetState();
    }

    @Test
    void bodyValidationErrorsListEachField() throws Exception {
        mockMvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \",\"topK\":50,\"minScore\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/search"))
                .andExpect(jsonPath("$.details.query").exists())
                .andExpect(jsonPath("$.details.topK").exists())
                .andExpect(jsonPath("$.details.minScore").exists());
    }

    @Test
    void requestParameterConstraintsAreValidated() throws Exception {
        mockMvc.perform(get("/api/v1/contracts?size=500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.size").exists());
        mockMvc.perform(get("/api/v1/contracts?status=SHREDDED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
    }

    @Test
    void malformedIdsAndBodies() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.id").value("must be a valid UUID"));
        mockMvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void notFoundUsesResourceNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/contracts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/contracts/" + id));
        mockMvc.perform(get("/api/v1/audits/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/no-such-endpoint")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ruleValidationAndDuplicateCodes() throws Exception {
        mockMvc.perform(post("/api/v1/compliance-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code\",\"title\":\"x\",\"question\":\"short\",\"guidance\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists())
                .andExpect(jsonPath("$.details.title").exists())
                .andExpect(jsonPath("$.details.question").exists())
                .andExpect(jsonPath("$.details.guidance").exists())
                .andExpect(jsonPath("$.details.severity").exists())
                .andExpect(jsonPath("$.details.mandatory").exists());
        mockMvc.perform(post("/api/v1/compliance-rules").contentType(MediaType.APPLICATION_JSON)
                        .content(rule("LIABILITY_CAP")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.code").exists());
        mockMvc.perform(put("/api/v1/compliance-rules/99999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Some title\",\"question\":\"A long enough question?\",\"guidance\":\"Long enough guidance.\","
                                + "\"severity\":\"LOW\",\"mandatory\":false,\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodIs405WithTheSamePayload() throws Exception {
        mockMvc.perform(put("/api/v1/search"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    static String rule(String code) {
        return "{\"code\":\"" + code + "\",\"title\":\"Warranty period\",\"question\":\"Does the contract define a warranty period?\","
                + "\"guidance\":\"Compliant: at least 12 months warranty.\",\"severity\":\"MEDIUM\",\"mandatory\":false}";
    }
}
