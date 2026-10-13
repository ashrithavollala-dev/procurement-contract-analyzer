package com.company.contractanalyzer.health;

import com.company.contractanalyzer.config.AppProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator component "embedding". The model is loaded at startup, and the startup dimension
 * check has already passed by the time this bean exists, so this reports the model's identity.
 */
@Component("embedding")
public class EmbeddingHealthIndicator implements HealthIndicator {

    public static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2 (ONNX)";

    private final EmbeddingModel embeddingModel;
    private final AppProperties properties;

    public EmbeddingHealthIndicator(EmbeddingModel embeddingModel, AppProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("model", MODEL_NAME)
                .withDetail("implementation", embeddingModel.getClass().getSimpleName())
                .withDetail("dimension", properties.getVector().getDimensions())
                .build();
    }
}
