package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.dto.response.DashboardSummaryResponse;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.mapper.AuditMapper;
import com.company.contractanalyzer.repository.AuditFindingRepository;
import com.company.contractanalyzer.repository.AuditRunRepository;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.StatusCount;
import com.company.contractanalyzer.service.DashboardService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates computed in SQL (GROUP BY / AVG / COUNT), not by loading rows into memory. */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int TOP_RULES = 5;

    private final ContractRepository contractRepository;
    private final AuditRunRepository auditRunRepository;
    private final AuditFindingRepository findingRepository;
    private final AuditMapper auditMapper;

    public DashboardServiceImpl(ContractRepository contractRepository, AuditRunRepository auditRunRepository,
                                AuditFindingRepository findingRepository, AuditMapper auditMapper) {
        this.contractRepository = contractRepository;
        this.auditRunRepository = auditRunRepository;
        this.findingRepository = findingRepository;
        this.auditMapper = auditMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        Map<ContractStatus, Long> byStatus = new EnumMap<>(ContractStatus.class);
        for (ContractStatus status : ContractStatus.values()) {
            byStatus.put(status, 0L);
        }
        long total = 0;
        for (StatusCount count : contractRepository.countGroupedByStatus()) {
            byStatus.put(count.status(), count.count());
            total += count.count();
        }
        Double average = auditRunRepository.averageCompletedRiskScore();
        return new DashboardSummaryResponse(
                total,
                byStatus,
                auditRunRepository.count(),
                average == null ? null : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP),
                auditRunRepository.findTop5ByOrderByCreatedAtDesc().stream().map(auditMapper::toSummary).toList(),
                findingRepository.topViolatedRules(List.of(FindingStatus.NON_COMPLIANT, FindingStatus.NOT_FOUND),
                        PageRequest.of(0, TOP_RULES)).stream().map(auditMapper::toStat).toList());
    }
}
