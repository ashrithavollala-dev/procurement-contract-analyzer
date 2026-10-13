package com.company.contractanalyzer.health;

/**
 * Whether the configured chat model can currently be used. LLM-dependent endpoints consult it
 * before queueing work (503 LLM_UNAVAILABLE), and GET /system/status reports it. Ingestion and
 * semantic search never depend on it.
 */
public interface LlmAvailability {

    LlmStatus status();

    default boolean isReachable() {
        return status().reachable();
    }

    /** Forces the next status() call to probe again (called after a connection failure). */
    void invalidate();
}
