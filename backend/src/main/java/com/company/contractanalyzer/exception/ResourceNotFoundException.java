package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, resource + " " + id + " was not found");
    }
}
