package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.ContractStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalContracts,
        Map<ContractStatus, Long> contractsByStatus,
        long totalAudits,
        BigDecimal averageRiskScore,
        List<AuditRunSummaryResponse> recentAudits,
        List<RuleViolationStat> topNonCompliantRules) {
}
