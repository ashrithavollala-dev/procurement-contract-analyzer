package com.company.contractanalyzer.service;

import com.company.contractanalyzer.dto.request.StartAuditRequest;
import com.company.contractanalyzer.dto.response.AuditRunDetailResponse;
import com.company.contractanalyzer.dto.response.AuditRunSummaryResponse;
import com.company.contractanalyzer.dto.response.StartAuditResponse;
import java.util.List;
import java.util.UUID;

public interface AuditService {

    /** Validates preconditions, persists a QUEUED run and dispatches it to the audit executor (HTTP 202). */
    StartAuditResponse start(UUID contractId, StartAuditRequest request);

    List<AuditRunSummaryResponse> listForContract(UUID contractId);

    AuditRunDetailResponse get(UUID auditId);
}
