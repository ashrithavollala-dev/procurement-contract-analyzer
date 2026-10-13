package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/** The chat model cannot be reached or is not configured (connection refused, missing model, missing key). */
public class LlmUnavailableException extends ApiException {

    public LlmUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.LLM_UNAVAILABLE, message);
    }
}
