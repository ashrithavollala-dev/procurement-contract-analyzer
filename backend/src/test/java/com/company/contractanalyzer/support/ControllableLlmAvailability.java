package com.company.contractanalyzer.support;

import com.company.contractanalyzer.health.LlmAvailability;
import com.company.contractanalyzer.health.LlmStatus;
import java.time.Instant;

/** Test double: lets a test declare the LLM reachable (default) or unreachable without any network probe. */
public class ControllableLlmAvailability implements LlmAvailability {

    private volatile boolean reachable = true;
    private volatile int invalidations;

    @Override
    public LlmStatus status() {
        return new LlmStatus("ollama", "llama3.2:3b", reachable,
                reachable ? "stub model (test)" : "Ollama not reachable (test)", Instant.now());
    }

    @Override
    public void invalidate() {
        invalidations++;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public int invalidations() {
        return invalidations;
    }
}
