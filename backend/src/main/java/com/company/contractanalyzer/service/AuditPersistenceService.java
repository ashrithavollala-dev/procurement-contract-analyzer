package com.company.contractanalyzer.service;

import com.company.contractanalyzer.ai.RuleEvaluation;
import com.company.contractanalyzer.entity.AuditFinding;
import com.company.contractanalyzer.entity.AuditRun;
import com.company.contractanalyzer.entity.ComplianceRule;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.AuditStatus;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.enums.RiskBand;
import com.company.contractanalyzer.repository.AuditFindingRepository;
import com.company.contractanalyzer.repository.AuditRunRepository;
import com.company.contractanalyzer.repository.ComplianceRuleRepository;
import com.company.contractanalyzer.repository.ContractRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit-run state changes and finding persistence, each in its own short transaction
 * (REQUIRES_NEW). The audit worker calls these between LLM calls and never holds a connection
 * or transaction across one. Saving a finding and incrementing {@code completedRules} share one
 * transaction, so the progress counter always equals the number of persisted findings.
 */
@Service
@RequiredArgsConstructor
public class AuditPersistenceService {

    private static final int MAX_TEXT = 4000;

    private final AuditRunRepository auditRunRepository;
    private final AuditFindingRepository findingRepository;
    private final ComplianceRuleRepository ruleRepository;
    private final ContractRepository contractRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditRun createQueued(UUID contractId, int totalRules) {
        Contract contract = contractRepository.getReferenceById(contractId);
        AuditRun run = new AuditRun(UUID.randomUUID());
        run.setContract(contract);
        run.setStatus(AuditStatus.QUEUED);
        run.setTotalRules(totalRules);
        run.setCompletedRules(0);
        return auditRunRepository.saveAndFlush(run);
    }

    /** QUEUED -> RUNNING. Returns false if the run vanished (contract deleted) or is no longer queued. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRunning(UUID runId) {
        return auditRunRepository.findById(runId)
                .filter(run -> run.getStatus() == AuditStatus.QUEUED)
                .map(run -> {
                    run.setStatus(AuditStatus.RUNNING);
                    run.setStartedAt(now());
                    auditRunRepository.saveAndFlush(run);
                    return true;
                })
                .orElse(false);
    }

    /** Persists one finding and advances the progress counter atomically. Returns false if the run is gone. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveFinding(UUID runId, Long ruleId, RuleEvaluation evaluation) {
        if (!auditRunRepository.existsById(runId)) {
            return false;
        }
        AuditFinding finding = new AuditFinding();
        finding.setAuditRun(auditRunRepository.getReferenceById(runId));
        ComplianceRule rule = ruleRepository.getReferenceById(ruleId);
        finding.setRule(rule);
        finding.setStatus(evaluation.status());
        finding.setConfidence(BigDecimal.valueOf(evaluation.confidence()).setScale(3, RoundingMode.HALF_UP));
        finding.setExplanation(truncate(evaluation.explanation()));
        finding.setEvidenceQuote(truncate(evaluation.evidenceQuote()));
        finding.setEvidencePage(evaluation.evidencePage());
        finding.setRecommendation(truncate(evaluation.recommendation()));
        finding.setIterations(evaluation.iterations());
        finding.setQueriesUsed(toJson(evaluation.queriesUsed()));
        finding.setErrorCode(evaluation.errorCode() == null ? null : evaluation.errorCode().name());
        findingRepository.saveAndFlush(finding);
        auditRunRepository.incrementCompletedRules(runId, now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID runId, BigDecimal riskScore, RiskBand band, String summary) {
        auditRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(AuditStatus.COMPLETED);
            run.setRiskScore(riskScore);
            run.setRiskBand(band);
            run.setSummary(summary);
            run.setErrorCode(null);
            run.setCompletedAt(now());
            auditRunRepository.saveAndFlush(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, ErrorCode code, String summary) {
        auditRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(AuditStatus.FAILED);
            run.setErrorCode(code.name());
            run.setSummary(summary);
            run.setCompletedAt(now());
            auditRunRepository.saveAndFlush(run);
        });
    }

    /** Startup recovery for audit runs left QUEUED/RUNNING by a previous process. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failInterrupted(Instant updatedBefore) {
        List<AuditRun> stuck = auditRunRepository.findByStatusInAndUpdatedAtBefore(
                List.of(AuditStatus.QUEUED, AuditStatus.RUNNING), updatedBefore);
        for (AuditRun run : stuck) {
            run.setStatus(AuditStatus.FAILED);
            run.setErrorCode(ErrorCode.INTERRUPTED.name());
            run.setSummary("Audit was interrupted by a backend restart after " + run.getCompletedRules() + " of "
                    + run.getTotalRules() + " rules. Start a new audit to retry.");
            run.setCompletedAt(now());
        }
        auditRunRepository.saveAllAndFlush(stuck);
        return stuck.size();
    }

    @Transactional(readOnly = true)
    public List<AuditFinding> findingsOf(UUID runId) {
        return findingRepository.findByAuditRunId(runId);
    }

    private String toJson(List<String> queries) {
        try {
            return objectMapper.writeValueAsString(queries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise queries", e);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT - 3) + "..." : text;
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
