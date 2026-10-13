package com.company.contractanalyzer.enums;

/** Lifecycle of a compliance audit run. */
public enum AuditStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isInFlight() {
        return this == QUEUED || this == RUNNING;
    }
}
