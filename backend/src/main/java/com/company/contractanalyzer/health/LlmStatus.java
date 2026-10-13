package com.company.contractanalyzer.health;

import java.time.Instant;

/**
 * Snapshot of chat-model reachability.
 *
 * @param detail short human-readable reason, never containing secrets
 */
public record LlmStatus(String provider, String model, boolean reachable, String detail, Instant checkedAt) {
}
