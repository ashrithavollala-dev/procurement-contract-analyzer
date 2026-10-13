package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.ai.RuleSpec;
import com.company.contractanalyzer.dto.request.StartAuditRequest;
import com.company.contractanalyzer.dto.response.AuditRunDetailResponse;
import com.company.contractanalyzer.dto.response.AuditRunSummaryResponse;
import com.company.contractanalyzer.dto.response.FindingResponse;
import com.company.contractanalyzer.dto.response.StartAuditResponse;
import com.company.contractanalyzer.entity.AuditRun;
import com.company.contractanalyzer.entity.ComplianceRule;
import com.company.contractanalyzer.enums.AuditStatus;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.exception.ApiException;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import com.company.contractanalyzer.exception.ResourceNotFoundException;
import com.company.contractanalyzer.health.LlmAvailability;
import com.company.contractanalyzer.health.LlmStatus;
import com.company.contractanalyzer.mapper.AuditMapper;
import com.company.contractanalyzer.mapper.ComplianceRuleMapper;
import com.company.contractanalyzer.repository.AuditFindingRepository;
import com.company.contractanalyzer.repository.AuditRunRepository;
import com.company.contractanalyzer.repository.ComplianceRuleRepository;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.service.AuditPersistenceService;
import com.company.contractanalyzer.service.AuditService;
import com.company.contractanalyzer.service.AuditWorker;
import com.company.contractanalyzer.service.ContractService;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final ContractService contractService;
    private final ContractRepository contractRepository;
    private final ComplianceRuleRepository ruleRepository;
    private final AuditRunRepository auditRunRepository;
    private final AuditFindingRepository findingRepository;
    private final AuditPersistenceService persistence;
    private final AuditWorker auditWorker;
    private final LlmAvailability llmAvailability;
    private final ComplianceRuleMapper ruleMapper;
    private final AuditMapper auditMapper;

    public AuditServiceImpl(ContractService contractService, ContractRepository contractRepository,
                            ComplianceRuleRepository ruleRepository, AuditRunRepository auditRunRepository,
                            AuditFindingRepository findingRepository, AuditPersistenceService persistence,
                            AuditWorker auditWorker, LlmAvailability llmAvailability, ComplianceRuleMapper ruleMapper,
                            AuditMapper auditMapper) {
        this.contractService = contractService;
        this.contractRepository = contractRepository;
        this.ruleRepository = ruleRepository;
        this.auditRunRepository = auditRunRepository;
        this.findingRepository = findingRepository;
        this.persistence = persistence;
        this.auditWorker = auditWorker;
        this.llmAvailability = llmAvailability;
        this.ruleMapper = ruleMapper;
        this.auditMapper = auditMapper;
    }

    @Override
    public StartAuditResponse start(UUID contractId, StartAuditRequest request) {
        contractService.requireReady(contractId);
        List<RuleSpec> rules = resolveRules(request).stream().map(ruleMapper::toSpec).toList();
        LlmStatus status = llmAvailability.status();
        if (!status.reachable()) {
            throw new LlmUnavailableException("The language model is not available: " + status.detail());
        }
        AuditRun run = persistence.createQueued(contractId, rules.size());
        try {
            auditWorker.run(run.getId(), contractId, rules);
        } catch (TaskRejectedException e) {
            persistence.fail(run.getId(), ErrorCode.AUDIT_QUEUE_FULL, "The audit queue was full; start the audit again later.");
            log.warn("Audit queue full; audit {} marked FAILED", run.getId());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AUDIT_QUEUE_FULL,
                    "Too many audits are queued; try again later", Map.of("auditId", run.getId().toString()));
        }
        log.info("Audit {} queued for contract {} with {} rule(s)", run.getId(), contractId, rules.size());
        return new StartAuditResponse(run.getId(), AuditStatus.QUEUED);
    }

    private List<ComplianceRule> resolveRules(StartAuditRequest request) {
        List<String> requested = request == null || request.ruleCodes() == null ? List.of() : request.ruleCodes();
        if (requested.isEmpty()) {
            List<ComplianceRule> active = ruleRepository.findByActiveTrueOrderByCodeAsc();
            if (active.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NO_ACTIVE_RULES,
                        "No compliance rule is active; activate at least one rule");
            }
            return active;
        }
        Set<String> codes = new LinkedHashSet<>(requested);
        List<ComplianceRule> found = ruleRepository.findByCodeInOrderByCodeAsc(codes);
        Set<String> unknown = new LinkedHashSet<>(codes);
        found.forEach(rule -> unknown.remove(rule.getCode()));
        if (!unknown.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("ruleCodes", "Unknown rule codes: " + String.join(", ", unknown)));
        }
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRunSummaryResponse> listForContract(UUID contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw new ResourceNotFoundException("Contract", contractId);
        }
        return auditRunRepository.findByContractIdOrderByCreatedAtDesc(contractId).stream()
                .map(auditMapper::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuditRunDetailResponse get(UUID auditId) {
        AuditRun run = auditRunRepository.findWithContractById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit", auditId));
        List<FindingResponse> findings = findingRepository.findByAuditRunId(auditId).stream()
                .map(auditMapper::toResponse)
                .sorted(Comparator.comparing((FindingResponse f) -> f.severity().ordinal()).reversed()
                        .thenComparing(FindingResponse::ruleCode))
                .toList();
        return auditMapper.toDetail(run, findings);
    }
}
