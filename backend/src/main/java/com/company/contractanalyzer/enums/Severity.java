package com.company.contractanalyzer.enums;

/** Rule severity and its risk-score weight. */
public enum Severity {
    LOW(1),
    MEDIUM(3),
    HIGH(5),
    CRITICAL(10);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    /** Weight used by the deterministic RiskScorer. */
    public int weight() {
        return weight;
    }
}
