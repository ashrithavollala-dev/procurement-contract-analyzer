package com.company.contractanalyzer.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test doubles for the AI models. The test profile disables the real chat and embedding
 * auto-configuration (spring.ai.model.chat=none, spring.ai.model.embedding=none), so these
 * are the only models in the context: no Ollama, no API key, no ONNX download.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestModelConfig {

    @Bean
    @Primary
    public StubChatModel stubChatModel() {
        return new StubChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
