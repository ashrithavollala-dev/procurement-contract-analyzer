package com.company.contractanalyzer.enums;

/** Lifecycle of an uploaded contract: UPLOADED -> QUEUED -> PROCESSING -> READY | FAILED. */
public enum ContractStatus {
    UPLOADED,
    QUEUED,
    PROCESSING,
    READY,
    FAILED;

    /** True while the ingestion pipeline owns the contract (the states the UI polls). */
    public boolean isInFlight() {
        return this == QUEUED || this == PROCESSING;
    }
}
