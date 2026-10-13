package com.company.contractanalyzer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.contractanalyzer.health.LlmStatus;
import com.company.contractanalyzer.health.LlmStatusService;
import com.company.contractanalyzer.support.AbstractIntegrationTest;
import com.company.contractanalyzer.support.FakeEmbeddingModel;
import com.company.contractanalyzer.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

/**
 * The test-profile context starts with no OPENAI_API_KEY and with nothing listening at the
 * configured Ollama URL. Only test doubles provide models, so no download and no network are
 * involved. The real LlmStatusService reports the outage instead of failing.
 */
class OfflineStartupIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private LlmStatusService realStatusService;
    @Value("${spring.ai.openai.api-key}") private String openAiKey;

    @Test
    void contextUsesOnlyTestDoublesAndNeedsNoKey() {
        assertThat(context.getBeansOfType(ChatModel.class).values())
                .singleElement().isInstanceOf(StubChatModel.class);
        assertThat(context.getBeansOfType(EmbeddingModel.class).values())
                .singleElement().isInstanceOf(FakeEmbeddingModel.class);
        if (System.getenv("OPENAI_API_KEY") == null) {
            assertThat(openAiKey).isEqualTo("not-configured");
        }
    }

    @Test
    void unreachableOllamaIsReportedByTheRealProbe() {
        LlmStatus status = realStatusService.status();

        assertThat(status.reachable()).isFalse();
        assertThat(status.detail()).contains("127.0.0.1:9");
    }

    @Test
    void healthAndStatusEndpointsWorkWithoutAnLlm() throws Exception {
        llmAvailability.setReachable(false);
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmReachable").value(false))
                .andExpect(jsonPath("$.llmProvider").value("ollama"))
                .andExpect(jsonPath("$.embeddingDimension").value(384))
                .andExpect(jsonPath("$.qaMode").value("rag"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.llm.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.max")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/executor.pool.max").param("tag", "name:ingestionExecutor"))
                .andExpect(status().isOk());
        llmAvailability.setReachable(true);
    }
}
