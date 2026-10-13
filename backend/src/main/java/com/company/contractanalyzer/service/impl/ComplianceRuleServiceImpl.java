package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.dto.request.CreateRuleRequest;
import com.company.contractanalyzer.dto.request.UpdateRuleRequest;
import com.company.contractanalyzer.dto.response.RuleResponse;
import com.company.contractanalyzer.entity.ComplianceRule;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.exception.ApiException;
import com.company.contractanalyzer.exception.ResourceNotFoundException;
import com.company.contractanalyzer.mapper.ComplianceRuleMapper;
import com.company.contractanalyzer.repository.ComplianceRuleRepository;
import com.company.contractanalyzer.service.ComplianceRuleService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceRuleServiceImpl implements ComplianceRuleService {

    private final ComplianceRuleRepository ruleRepository;
    private final ComplianceRuleMapper mapper;

    public ComplianceRuleServiceImpl(ComplianceRuleRepository ruleRepository, ComplianceRuleMapper mapper) {
        this.ruleRepository = ruleRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleResponse> list(boolean activeOnly) {
        List<ComplianceRule> rules = activeOnly
                ? ruleRepository.findByActiveTrueOrderByCodeAsc()
                : ruleRepository.findAllByOrderByCodeAsc();
        return rules.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public RuleResponse create(CreateRuleRequest request) {
        if (ruleRepository.existsByCode(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("code", "a rule with code " + request.code() + " already exists"));
        }
        ComplianceRule saved = ruleRepository.saveAndFlush(mapper.toEntity(request));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RuleResponse update(Long id, UpdateRuleRequest request) {
        ComplianceRule rule = find(id);
        mapper.update(request, rule);
        return mapper.toResponse(ruleRepository.saveAndFlush(rule));
    }

    @Override
    @Transactional
    public RuleResponse setActive(Long id, boolean active) {
        ComplianceRule rule = find(id);
        rule.setActive(active);
        return mapper.toResponse(ruleRepository.saveAndFlush(rule));
    }

    private ComplianceRule find(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compliance rule", id));
    }
}
