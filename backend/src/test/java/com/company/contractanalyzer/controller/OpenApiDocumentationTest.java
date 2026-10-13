package com.company.contractanalyzer.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The springdoc OpenAPI document exposes exactly the endpoints and methods of docs/API_CONTRACT.md. */
class OpenApiDocumentationTest extends AbstractIntegrationTest {

    @Test
    void everyContractEndpointIsPublished() throws Exception {
        JsonNode paths = getJson("/v3/api-docs").get("paths");

        assertMethods(paths, "/api/v1/contracts", "post", "get");
        assertMethods(paths, "/api/v1/contracts/{id}", "get", "delete");
        assertMethods(paths, "/api/v1/contracts/{id}/file", "get");
        assertMethods(paths, "/api/v1/contracts/{id}/reprocess", "post");
        assertMethods(paths, "/api/v1/search", "post");
        assertMethods(paths, "/api/v1/contracts/{contractId}/audits", "post", "get");
        assertMethods(paths, "/api/v1/audits/{auditId}", "get");
        assertMethods(paths, "/api/v1/contracts/{contractId}/ask", "post");
        assertMethods(paths, "/api/v1/compliance-rules", "get", "post");
        assertMethods(paths, "/api/v1/compliance-rules/{id}", "put");
        assertMethods(paths, "/api/v1/compliance-rules/{id}/active", "patch");
        assertMethods(paths, "/api/v1/dashboard/summary", "get");
        assertMethods(paths, "/api/v1/system/status", "get");
        assertThat(paths.size()).isEqualTo(13);
    }

    @Test
    void schemasDoNotLeakEntities() throws Exception {
        JsonNode schemas = getJson("/v3/api-docs").get("components").get("schemas");

        assertThat(schemas.has("Contract")).isFalse();
        assertThat(schemas.has("AuditRun")).isFalse();
        assertThat(schemas.get("ContractResponse").get("properties").has("storedPath")).isFalse();
        assertThat(schemas.get("ErrorResponse").get("properties").has("code")).isTrue();
    }

    private static void assertMethods(JsonNode paths, String path, String... methods) {
        assertThat(paths.has(path)).as("path %s", path).isTrue();
        assertThat(List.of(methods)).allSatisfy(m -> assertThat(paths.get(path).has(m)).as("%s %s", m, path).isTrue());
    }
}
