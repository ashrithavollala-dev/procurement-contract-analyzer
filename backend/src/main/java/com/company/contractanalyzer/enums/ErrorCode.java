package com.company.contractanalyzer.enums;

/** Machine-readable error codes shared by the API error payload and persisted failure states. */
public enum ErrorCode {
    RESOURCE_NOT_FOUND,
    VALIDATION_FAILED,
    UNSUPPORTED_FILE_TYPE,
    FILE_TOO_LARGE,
    DUPLICATE_CONTRACT,
    CONTRACT_NOT_READY,
    NO_ACTIVE_RULES,
    NO_EXTRACTABLE_TEXT,
    INGESTION_QUEUE_FULL,
    AUDIT_QUEUE_FULL,
    INTERRUPTED,
    LLM_UNAVAILABLE,
    LLM_TIMEOUT,
    LLM_OUTPUT_INVALID,
    INTERNAL_ERROR
}
