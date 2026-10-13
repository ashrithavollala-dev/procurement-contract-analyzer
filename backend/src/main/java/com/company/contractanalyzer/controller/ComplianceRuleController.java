package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.request.CreateRuleRequest;
import com.company.contractanalyzer.dto.request.RuleActiveRequest;
import com.company.contractanalyzer.dto.request.UpdateRuleRequest;
import com.company.contractanalyzer.dto.response.RuleResponse;
import com.company.contractanalyzer.service.ComplianceRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compliance-rules")
@Tag(name = "Compliance rules", description = "Configurable rule set used by audits")
public class ComplianceRuleController {

    private final ComplianceRuleService ruleService;

    public ComplianceRuleController(ComplianceRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Operation(summary = "List rules ordered by code")
    @GetMapping
    public List<RuleResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ruleService.list(activeOnly);
    }

    @Operation(summary = "Create a rule")
    @PostMapping
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody CreateRuleRequest request) {
        RuleResponse created = ruleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/compliance-rules/" + created.id())).body(created);
    }

    @Operation(summary = "Replace a rule's editable fields (code is immutable)")
    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRuleRequest request) {
        return ruleService.update(id, request);
    }

    @Operation(summary = "Activate or deactivate a rule")
    @PatchMapping("/{id}/active")
    public RuleResponse setActive(@PathVariable Long id, @Valid @RequestBody RuleActiveRequest request) {
        return ruleService.setActive(id, request.active());
    }
}
