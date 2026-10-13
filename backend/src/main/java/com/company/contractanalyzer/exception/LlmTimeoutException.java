package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/** A chat model call (or waiting for a free LLM slot) exceeded its time budget. Not counted as "unreachable". */
public class LlmTimeoutException extends ApiException {

    public LlmTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.LLM_TIMEOUT, message);
    }
}
