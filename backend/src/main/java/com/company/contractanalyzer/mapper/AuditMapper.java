package com.company.contractanalyzer.mapper;

import com.company.contractanalyzer.dto.response.AuditRunDetailResponse;
import com.company.contractanalyzer.dto.response.AuditRunSummaryResponse;
import com.company.contractanalyzer.dto.response.FindingResponse;
import com.company.contractanalyzer.dto.response.RuleViolationStat;
import com.company.contractanalyzer.entity.AuditFinding;
import com.company.contractanalyzer.entity.AuditRun;
import com.company.contractanalyzer.repository.RuleViolationCount;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Audit entities -> DTOs. Callers load {@code contract} and {@code rule} with entity graphs,
 * so no lazy loading happens outside a transaction (open-in-view is disabled).
 */
@Mapper(uses = QueriesUsedCodec.class)
public interface AuditMapper {

    @Mapping(target = "contractId", source = "contract.id")
    @Mapping(target = "contractTitle", source = "contract.title")
    AuditRunSummaryResponse toSummary(AuditRun run);

    @Mapping(target = "id", source = "run.id")
    @Mapping(target = "contractId", source = "run.contract.id")
    @Mapping(target = "contractTitle", source = "run.contract.title")
    @Mapping(target = "status", source = "run.status")
    @Mapping(target = "totalRules", source = "run.totalRules")
    @Mapping(target = "completedRules", source = "run.completedRules")
    @Mapping(target = "riskScore", source = "run.riskScore")
    @Mapping(target = "riskBand", source = "run.riskBand")
    @Mapping(target = "summary", source = "run.summary")
    @Mapping(target = "errorCode", source = "run.errorCode")
    @Mapping(target = "startedAt", source = "run.startedAt")
    @Mapping(target = "completedAt", source = "run.completedAt")
    @Mapping(target = "createdAt", source = "run.createdAt")
    @Mapping(target = "findings", source = "findings")
    AuditRunDetailResponse toDetail(AuditRun run, List<FindingResponse> findings);

    @Mapping(target = "ruleCode", source = "rule.code")
    @Mapping(target = "ruleTitle", source = "rule.title")
    @Mapping(target = "severity", source = "rule.severity")
    @Mapping(target = "mandatory", source = "rule.mandatory")
    FindingResponse toResponse(AuditFinding finding);

    RuleViolationStat toStat(RuleViolationCount count);
}
