package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.request.StartAuditRequest;
import com.company.contractanalyzer.dto.response.AuditRunDetailResponse;
import com.company.contractanalyzer.dto.response.AuditRunSummaryResponse;
import com.company.contractanalyzer.dto.response.StartAuditResponse;
import com.company.contractanalyzer.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Audits", description = "Agentic compliance audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Start a compliance audit (all active rules, or the given ruleCodes)")
    @ApiResponse(responseCode = "202", description = "Audit queued")
    @PostMapping("/contracts/{contractId}/audits")
    public ResponseEntity<StartAuditResponse> start(@PathVariable UUID contractId,
                                                    @Valid @RequestBody(required = false) StartAuditRequest request) {
        StartAuditResponse response = auditService.start(contractId, request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/audits/" + response.auditId())).body(response);
    }

    @Operation(summary = "List a contract's audits, newest first")
    @GetMapping("/contracts/{contractId}/audits")
    public List<AuditRunSummaryResponse> list(@PathVariable UUID contractId) {
        return auditService.listForContract(contractId);
    }

    @Operation(summary = "Get an audit with progress and findings")
    @GetMapping("/audits/{auditId}")
    public AuditRunDetailResponse get(@PathVariable UUID auditId) {
        return auditService.get(auditId);
    }
}
