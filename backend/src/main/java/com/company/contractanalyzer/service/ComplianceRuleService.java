package com.company.contractanalyzer.service;

import com.company.contractanalyzer.dto.request.CreateRuleRequest;
import com.company.contractanalyzer.dto.request.UpdateRuleRequest;
import com.company.contractanalyzer.dto.response.RuleResponse;
import java.util.List;

public interface ComplianceRuleService {

    List<RuleResponse> list(boolean activeOnly);

    RuleResponse create(CreateRuleRequest request);

    RuleResponse update(Long id, UpdateRuleRequest request);

    RuleResponse setActive(Long id, boolean active);
}
