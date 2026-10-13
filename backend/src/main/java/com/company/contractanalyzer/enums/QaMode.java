package com.company.contractanalyzer.enums;

/** Q&A strategy: retrieval-augmented generation (default) or a bounded tool-calling agent. */
public enum QaMode {
    RAG,
    TOOLS;

    /** Lowercase representation used in configuration and in API responses. */
    public String value() {
        return name().toLowerCase();
    }
}
