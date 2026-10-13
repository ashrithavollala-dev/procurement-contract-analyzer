package com.company.contractanalyzer.enums;

/** Outcome of evaluating one rule against one contract. */
public enum FindingStatus {
    COMPLIANT,
    PARTIAL,
    NON_COMPLIANT,
    NOT_FOUND,
    NEEDS_REVIEW;

    /** Statuses that assert something about a clause and therefore must carry verifiable evidence. */
    public boolean requiresEvidence() {
        return this == COMPLIANT || this == PARTIAL || this == NON_COMPLIANT;
    }
}
