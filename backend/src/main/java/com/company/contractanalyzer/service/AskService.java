package com.company.contractanalyzer.service;

import com.company.contractanalyzer.dto.request.AskRequest;
import com.company.contractanalyzer.dto.response.AskResponse;
import java.util.UUID;

public interface AskService {

    /** Synchronous grounded Q&A (bounded by QA_TIMEOUT_SECONDS). */
    AskResponse ask(UUID contractId, AskRequest request);
}
