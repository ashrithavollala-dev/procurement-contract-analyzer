package com.company.contractanalyzer.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmStatusServiceTest {

    @Test
    void unreachableOllamaIsReportedNotThrown() {
        AppProperties properties = new AppProperties();
        properties.getLlm().setOllamaBaseUrl("http://127.0.0.1:9");
        properties.getLlm().setProbeTimeout(Duration.ofMillis(500));

        LlmStatus status = service(properties).status();

        assertThat(status.reachable()).isFalse();
        assertThat(status.provider()).isEqualTo("ollama");
        assertThat(status.model()).isEqualTo("llama3.2:3b");
        assertThat(status.detail()).contains("not reachable");
    }

    @Test
    void openAiWithoutKeyIsUnreachableAndWithKeyIsReachable() {
        AppProperties properties = new AppProperties();
        properties.getLlm().setProvider("openai");
        properties.getLlm().setOpenaiApiKey(LlmStatusService.KEY_SENTINEL);
        assertThat(service(properties).status().reachable()).isFalse();
        assertThat(service(properties).status().detail()).contains("OPENAI_API_KEY");

        properties.getLlm().setOpenaiApiKey("sk-test-placeholder");
        LlmStatus status = service(properties).status();
        assertThat(status.reachable()).isTrue();
        assertThat(status.detail()).doesNotContain("sk-test");
    }

    @Test
    void modelMustBePulled() throws Exception {
        LlmStatusService service = service(new AppProperties());
        String tags = "{\"models\":[{\"name\":\"llama3.2:3b\"},{\"name\":\"mistral:latest\"}]}";

        assertThat(service.modelIsPulled(tags, "llama3.2:3b")).isTrue();
        assertThat(service.modelIsPulled(tags, "mistral")).isTrue();
        assertThat(service.modelIsPulled(tags, "llama3.1:8b")).isFalse();
        assertThat(service.modelIsPulled("{\"models\":[]}", "llama3.2:3b")).isFalse();
    }

    @Test
    void resultIsCachedUntilInvalidated() {
        AppProperties properties = new AppProperties();
        properties.getLlm().setProvider("openai");
        properties.getLlm().setOpenaiApiKey("sk-test-placeholder");
        properties.getLlm().setStatusCacheTtl(Duration.ofMinutes(5));
        LlmStatusService service = service(properties);

        LlmStatus first = service.status();
        assertThat(service.status()).isSameAs(first);
        service.invalidate();
        assertThat(service.status()).isNotSameAs(first);
    }

    private static LlmStatusService service(AppProperties properties) {
        return new LlmStatusService(properties, new ObjectMapper(), Clock.systemUTC());
    }
}
