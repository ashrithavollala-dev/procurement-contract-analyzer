package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * The chat model cannot be used. {@code connectionFailure} distinguishes "unreachable"
 * (connection refused, unknown host, missing model, missing key) from other provider errors.
 * Only the former counts toward the audit circuit breaker.
 */
public class LlmUnavailableException extends ApiException {

    private final boolean connectionFailure;

    public LlmUnavailableException(String message) {
        this(message, true);
    }

    public LlmUnavailableException(String message, boolean connectionFailure) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.LLM_UNAVAILABLE, message);
        this.connectionFailure = connectionFailure;
    }

    public boolean isConnectionFailure() {
        return connectionFailure;
    }
}
