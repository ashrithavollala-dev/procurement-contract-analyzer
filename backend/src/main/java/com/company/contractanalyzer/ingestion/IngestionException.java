package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.enums.ErrorCode;

/**
 * Expected ingestion failure with a code and a sanitised, user-facing message. The message
 * is stored on the contract row as is, so it must never contain contract text or stack traces.
 */
public class IngestionException extends RuntimeException {

    private final ErrorCode code;

    public IngestionException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public IngestionException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
