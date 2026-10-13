package com.company.contractanalyzer.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator component "llm". An unreachable model is reported as UNKNOWN, not DOWN: the
 * application stays healthy (ingestion and search work), so container healthchecks and
 * readiness are unaffected. Only the LLM features degrade, with 503 LLM_UNAVAILABLE.
 */
@Component("llm")
public class LlmHealthIndicator implements HealthIndicator {

    private final LlmAvailability availability;

    public LlmHealthIndicator(LlmAvailability availability) {
        this.availability = availability;
    }

    @Override
    public Health health() {
        LlmStatus status = availability.status();
        Health.Builder builder = status.reachable() ? Health.up() : Health.unknown();
        return builder
                .withDetail("provider", status.provider())
                .withDetail("model", status.model())
                .withDetail("reachable", status.reachable())
                .withDetail("detail", status.detail())
                .build();
    }
}
