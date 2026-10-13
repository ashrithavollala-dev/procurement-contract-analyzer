package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.enums.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Business error carrying everything the uniform error payload needs. Messages are written for
 * API clients: they never contain stack traces, file system paths or contract text.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, Map<String, ?> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
