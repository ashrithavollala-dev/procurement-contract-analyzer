package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.AuditStatus;
import java.util.UUID;

public record StartAuditResponse(UUID auditId, AuditStatus status) {
}
