package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.AuditStatus;
import com.company.contractanalyzer.enums.RiskBand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Summary fields plus findings; polled by the UI for progress (completedRules / totalRules). */
public record AuditRunDetailResponse(
        UUID id,
        UUID contractId,
        String contractTitle,
        AuditStatus status,
        int totalRules,
        int completedRules,
        BigDecimal riskScore,
        RiskBand riskBand,
        String summary,
        String errorCode,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        List<FindingResponse> findings) {
}
